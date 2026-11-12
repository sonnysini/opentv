package app.opentv.ui.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestartKeyTest {
    @Test
    fun parsesTheLivePlayersKey() {
        assertEquals(RestartKey(42L, 1_000L, 5_000L), RestartKey.parse("restart:42:1000:5000"))
    }

    @Test
    fun otherKeysAreNotRestarts() {
        assertNull(RestartKey.parse("catchup:42:1000"))
        assertNull(RestartKey.parse("movie:7"))
        assertNull(RestartKey.parse("restart:42:5000:1000"))
        assertNull(RestartKey.parse("restart:x:1000:5000"))
    }

    @Test
    fun movesTheArchiveLinkToAnotherMinute() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        // 07:20 to 07:50 UTC on 12 Nov 2026.
        val key = RestartKey(1L, 1_794_468_000_000L, 1_794_469_800_000L)
        val url = "http://h:80/timeshift/u/p/30/2026-11-12:07-20/402672.ts"
        assertEquals("http://h:80/timeshift/u/p/30/2026-11-12:07-20/402672.ts", key.urlFrom(url, 0, utc))
        assertEquals("http://h:80/timeshift/u/p/18/2026-11-12:07-32/402672.ts", key.urlFrom(url, 12, utc))
    }
}
