/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.HiccupWatch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Short pauses add up: enough of them inside the window means the stream keeps stuttering. */
class HiccupWatchTest {

    /** Plays smoothly from [from] for [millis], then sits still for [pause]; returns the last verdict and end time. */
    private fun HiccupWatch.run(from: Long, millis: Long, pause: Long, pos: LongArray): Pair<Boolean, Long> {
        var t = from
        var hit = false
        while (t < from + millis) {
            pos[0] += 250
            hit = sample(t, pos[0], pos[0] / 40, true) || hit
            t += 250
        }
        val end = t + pause
        while (t < end) {
            hit = sample(t, pos[0], pos[0] / 40, true) || hit
            t += 250
        }
        return hit to t
    }

    @Test
    fun `steady playback never trips`() {
        val w = HiccupWatch(3)
        for (t in 0..400) assertThat(w.sample(t * 250L, t * 250L, t * 6L, true)).isFalse()
    }

    @Test
    fun `pauses shorter than the minimum do not count`() {
        val w = HiccupWatch(2)
        val pos = longArrayOf(0)
        var t = 0L
        repeat(10) { val (hit, end) = w.run(t, 2_000, 500, pos); assertThat(hit).isFalse(); t = end }
    }

    @Test
    fun `one long pause counts once`() {
        val w = HiccupWatch(2)
        val pos = longArrayOf(0)
        val (hit, _) = w.run(0, 2_000, 5_000, pos)
        assertThat(hit).isFalse()
    }

    @Test
    fun `enough pauses within the window trip`() {
        val w = HiccupWatch(3)
        val pos = longArrayOf(0)
        var t = 0L
        var (hit, end) = w.run(t, 5_000, 1_000, pos); assertThat(hit).isFalse(); t = end
        w.run(t, 5_000, 1_000, pos).also { assertThat(it.first).isFalse(); t = it.second }
        w.run(t, 5_000, 1_000, pos).also { hit = it.first }
        assertThat(hit).isTrue()
    }

    @Test
    fun `pauses spread beyond the window do not add up`() {
        val w = HiccupWatch(3)
        val pos = longArrayOf(0)
        var t = 0L
        repeat(6) {
            val (hit, end) = w.run(t, 30_000, 1_000, pos)
            assertThat(hit).isFalse()
            t = end
        }
    }

    @Test
    fun `reset forgets earlier pauses`() {
        val w = HiccupWatch(2)
        val pos = longArrayOf(0)
        val (_, end) = w.run(0, 2_000, 1_000, pos)
        w.reset()
        val (hit, _) = w.run(end, 2_000, 1_000, pos)
        assertThat(hit).isFalse()
    }

    @Test
    fun `limit zero is off`() {
        val w = HiccupWatch(0)
        val pos = longArrayOf(0)
        var t = 0L
        repeat(5) { val (hit, end) = w.run(t, 2_000, 1_000, pos); assertThat(hit).isFalse(); t = end }
    }
}
