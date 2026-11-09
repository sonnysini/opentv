package app.opentv.data.repo

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideDailyTest {
    private val rome = TimeZone.getTimeZone("Europe/Rome")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(rome).apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute)
        }.timeInMillis

    @Test fun sameDayIsFresh() {
        assertTrue(EpgRepository.syncedToday(at(9, 7), at(9, 23, 59), rome))
    }

    @Test fun nextDayIsStale() {
        assertFalse(EpgRepository.syncedToday(at(8, 23, 30), at(9, 0, 10), rome))
    }

    @Test fun neverSyncedIsStale() {
        assertFalse(EpgRepository.syncedToday(0L, at(9, 10), rome))
    }

    @Test fun stampInTheFutureIsStale() {
        assertFalse(EpgRepository.syncedToday(at(10, 8), at(9, 10), rome))
    }
}
