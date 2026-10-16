package app.opentv

import app.opentv.data.remote.ThrottledInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ThrottledInputStreamTest {

    @Test
    fun `passes bytes through unchanged`() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val read = ThrottledInputStream(ByteArrayInputStream(data), 0).readBytes()
        assertArrayEquals(data, read)
    }

    @Test
    fun `caps the read rate`() {
        // 200 KB at 400 KB/s should take about half a second.
        val data = ByteArray(200 * 1024)
        val start = System.nanoTime()
        val read = ThrottledInputStream(ByteArrayInputStream(data), 400L * 1024).readBytes()
        val millis = (System.nanoTime() - start) / 1_000_000
        assertArrayEquals(data, read)
        assertTrue("took $millis ms", millis in 400..2000)
    }
}
