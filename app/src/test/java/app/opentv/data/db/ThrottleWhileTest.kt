/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleWhileTest {

    @Test
    fun `passes every signal straight through when not busy`() = runTest {
        val upstream = Channel<Unit>(Channel.UNLIMITED)
        val busy = MutableStateFlow(false)
        val out = mutableListOf<Long>()
        val job = launch {
            upstream.consumeAsFlow().throttleWhile(busy, 20_000) { testScheduler.currentTime }
                .collect { out += testScheduler.currentTime }
        }
        repeat(3) { upstream.send(Unit); runCurrent() }
        advanceTimeBy(1_000); upstream.send(Unit); runCurrent()
        assertThat(out).hasSize(4)
        job.cancel()
    }

    @Test
    fun `while busy lets one signal through per period and the last one when it ends`() = runTest {
        val upstream = Channel<Unit>(Channel.UNLIMITED)
        val busy = MutableStateFlow(false)
        val out = mutableListOf<Long>()
        val job = launch {
            upstream.consumeAsFlow().throttleWhile(busy, 20_000) { testScheduler.currentTime }
                .collect { out += testScheduler.currentTime }
        }
        runCurrent()
        busy.value = true
        runCurrent()
        // A download: a batch every 100 ms for 50 s.
        repeat(500) {
            upstream.send(Unit)
            runCurrent()
            advanceTimeBy(100)
        }
        runCurrent()
        val during = out.size
        // First batch at once, then at most one per 20 s over the 50 s.
        assertThat(during).isAtMost(4)
        assertThat(during).isAtLeast(2)
        busy.value = false
        runCurrent()
        // The held-back last batch arrives as soon as the download ends.
        assertThat(out.size).isEqualTo(during + 1)
        job.cancel()
    }

    @Test
    fun `a held signal is delivered when the period runs out even if nothing else comes`() = runTest {
        val upstream = Channel<Unit>(Channel.UNLIMITED)
        val busy = MutableStateFlow(true)
        val out = mutableListOf<Long>()
        val job = launch {
            upstream.consumeAsFlow().throttleWhile(busy, 20_000) { testScheduler.currentTime }
                .collect { out += testScheduler.currentTime }
        }
        upstream.send(Unit); runCurrent() // through at once
        advanceTimeBy(1_000)
        upstream.send(Unit); runCurrent() // held
        assertThat(out).hasSize(1)
        advanceTimeBy(19_500)
        runCurrent()
        assertThat(out).hasSize(2)
        job.cancel()
    }

    @Test
    fun `ends with the upstream, delivering what was held`() = runTest {
        val upstream = Channel<Unit>(Channel.UNLIMITED)
        val busy = MutableStateFlow(true)
        val out = mutableListOf<Long>()
        val job = launch {
            upstream.consumeAsFlow().throttleWhile(busy, 20_000) { testScheduler.currentTime }
                .collect { out += testScheduler.currentTime }
        }
        upstream.send(Unit); runCurrent()
        upstream.send(Unit); runCurrent()
        upstream.close()
        runCurrent()
        assertThat(out).hasSize(2)
        assertThat(job.isCompleted).isTrue()
    }
}
