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
}
