/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One signal on collection, then one each time a write to any of [tables] commits — what a Room
 * `Flow` query listens to, without the query. Lets a caller decide *when* a heavy read is worth
 * running instead of re-running it on every commit (see [throttleWhile]).
 */
fun RoomDatabase.tableChanges(vararg tables: String): Flow<Unit> = callbackFlow {
    val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(changed: Set<String>) {
            trySend(Unit)
        }
    }
    // Registered before the first signal, so a commit landing in between isn't missed.
    invalidationTracker.addObserver(observer)
    trySend(Unit)
    awaitClose { invalidationTracker.removeObserver(observer) }
}.conflate()

/**
 * Passes signals straight through while [busy] is false. While it is true (a guide download
 * writing hundreds of batches), at most one signal per [periodMillis] gets through, and the last
 * one held back is delivered the moment [busy] turns false — so the consumer ends on the final
 * state, just without redoing its work for every batch on the way.
 */
fun Flow<Unit>.throttleWhile(
    busy: StateFlow<Boolean>,
    periodMillis: Long,
    now: () -> Long = { System.nanoTime() / 1_000_000 },
): Flow<Unit> = channelFlow {
    // One queue for both inputs keeps all the bookkeeping on this single loop.
    val signals = Channel<Signal>(Channel.UNLIMITED)
    launch {
        collect { signals.send(Signal.CHANGE) }
        signals.send(Signal.END)
    }
    launch { busy.collect { signals.send(Signal.GATE) } }

    var pending = false
    var lastSent = Long.MIN_VALUE / 2
    suspend fun fire() {
        pending = false
        lastSent = now()
        send(Unit)
    }
    while (true) {
        val signal = if (pending) {
            val wait = (lastSent + periodMillis - now()).coerceAtLeast(0L)
            withTimeoutOrNull(wait) { signals.receive() } ?: Signal.TIMER
        } else {
            signals.receive()
        }
        when (signal) {
            Signal.CHANGE ->
                if (!busy.value || now() - lastSent >= periodMillis) fire() else pending = true
            Signal.GATE -> if (pending && !busy.value) fire()
            Signal.TIMER -> fire()
            Signal.END -> {
                if (pending) fire()
                break
            }
        }
    }
    // The gate is a StateFlow and never completes: stop its collector so the flow can end.
    coroutineContext.cancelChildren()
}

private enum class Signal { CHANGE, GATE, TIMER, END }
