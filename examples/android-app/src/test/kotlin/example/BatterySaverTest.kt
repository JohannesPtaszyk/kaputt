package example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatterySaverTest {

    @Test
    fun `syncs while charging`() {
        assertTrue(BatterySaver.maySync(batteryPercent = 5, charging = true))
    }

    @Test
    fun `syncs on a healthy battery`() {
        assertTrue(BatterySaver.maySync(batteryPercent = 80, charging = false))
    }

    @Test
    fun `waits on a flat battery`() {
        assertFalse(BatterySaver.maySync(batteryPercent = 5, charging = false))
    }

    @Test
    fun `backs off further after each failure`() {
        assertEquals(10, BatterySaver.backoffMinutes(consecutiveFailures = 2))
    }
}
