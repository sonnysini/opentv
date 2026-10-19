/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.StallWatch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A stream counts as stalled only when neither position nor (with video) frames move for the limit. */
class StallWatchTest {

    @Test
    fun `steady playback never stalls`() {
        val w = StallWatch(3_000)
        for (t in 0..20) assertThat(w.sample(t * 250L, t * 250L, t * 6L, true)).isFalse()
    }

    @Test
    fun `stuck position stalls after the limit`() {
        val w = StallWatch(3_000)
        assertThat(w.sample(0, 1_000, 10, true)).isFalse()
        assertThat(w.sample(2_750, 1_000, 10, true)).isFalse()
        assertThat(w.sample(3_000, 1_000, 10, true)).isTrue()
    }

    @Test
    fun `frozen picture with audio still running is a stall`() {
        val w = StallWatch(1_000)
        assertThat(w.sample(0, 0, 50, true)).isFalse()
        assertThat(w.sample(500, 500, 50, true)).isFalse()
        assertThat(w.sample(1_000, 1_000, 50, true)).isTrue()
    }

    @Test
    fun `audio only streams are judged by position`() {
        val w = StallWatch(1_000)
        assertThat(w.sample(0, 0, 0, false)).isFalse()
        assertThat(w.sample(2_000, 2_000, 0, false)).isFalse()
        assertThat(w.sample(3_000, 2_000, 0, false)).isTrue()
    }

    @Test
    fun `reset starts the clock over`() {
        val w = StallWatch(1_000)
        w.sample(0, 0, 0, true)
        w.reset()
        assertThat(w.sample(5_000, 0, 0, true)).isFalse()
        assertThat(w.sample(5_900, 0, 0, true)).isFalse()
        assertThat(w.sample(6_000, 0, 0, true)).isTrue()
    }

    @Test
    fun `next variant wraps round and skips the current one`() {
        val order = listOf(1L, 2L, 3L)
        assertThat(StallWatch.nextAfter(order, 1L) { it }).isEqualTo(2L)
        assertThat(StallWatch.nextAfter(order, 3L) { it }).isEqualTo(1L)
        assertThat(StallWatch.nextAfter(order, 9L) { it }).isEqualTo(1L)
        assertThat(StallWatch.nextAfter(listOf(1L), 1L) { it }).isNull()
    }
}
