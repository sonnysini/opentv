package app.opentv

import app.opentv.core.RefreshRate
import app.opentv.core.RefreshRate.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshRateTest {
    // The Fire TV Stick 4K list, trimmed.
    private val modes = listOf(
        Mode(1, 1920, 1080, 60.000004f),
        Mode(2, 1920, 1080, 59.94f),
        Mode(3, 1280, 720, 59.94f),
        Mode(4, 1920, 1080, 50f),
        Mode(5, 1920, 1080, 29.97f),
        Mode(6, 1920, 1080, 23.976f),
        Mode(7, 1920, 1080, 24f),
        Mode(8, 1920, 1080, 25f),
        Mode(10, 1280, 720, 50f),
    )
    private val at5994 = modes[1]

    @Test fun italianChannelsGoTo50() {
        assertEquals(4, RefreshRate.pick(modes, at5994, 25f)?.id)
        assertEquals(4, RefreshRate.pick(modes, at5994, 50f)?.id)
    }

    @Test fun ntscStaysPut() {
        assertNull(RefreshRate.pick(modes, at5994, 29.97f))
        assertNull(RefreshRate.pick(modes, at5994, 59.94f))
    }

    @Test fun filmsGoToTheirOwnRate() {
        assertEquals(6, RefreshRate.pick(modes, at5994, 23.976f)?.id)
        assertEquals(7, RefreshRate.pick(modes, at5994, 24f)?.id)
    }

    @Test fun sixtyWithDecimalsCounts() {
        assertEquals(1, RefreshRate.pick(modes, modes[3], 30f)?.id)
    }

    @Test fun keepsTheResolution() {
        assertEquals(10, RefreshRate.pick(modes, modes[2], 25f)?.id)
    }

    @Test fun unknownRateDoesNothing() {
        assertNull(RefreshRate.pick(modes, at5994, 0f))
    }

    @Test fun homeIs50AtTheSameResolution() {
        assertEquals(4, RefreshRate.home(modes, at5994)?.id)
        assertEquals(10, RefreshRate.home(modes, modes[2])?.id)
    }

    @Test fun homeKeepsAFittingMode() {
        assertEquals(4, RefreshRate.home(modes, modes[3])?.id)
    }

    @Test fun homeIsNullWithout50() {
        assertNull(RefreshRate.home(listOf(at5994), at5994))
    }
}
