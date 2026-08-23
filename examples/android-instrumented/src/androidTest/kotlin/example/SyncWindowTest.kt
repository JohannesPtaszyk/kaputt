package example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWindowTest {

    @Test
    fun syncsOnUnmeteredNetworkWithBattery() {
        assertTrue(SyncWindow.maySync(batteryPercent = 80, metered = false))
    }

    @Test
    fun waitsOnMeteredNetwork() {
        assertFalse(SyncWindow.maySync(batteryPercent = 80, metered = true))
    }

    @Test
    fun waitsOnFlatBattery() {
        assertFalse(SyncWindow.maySync(batteryPercent = 5, metered = false))
    }

    @Test
    fun backsOffFurtherAfterEachFailure() {
        assertEquals(10, SyncWindow.backoffMinutes(consecutiveFailures = 2))
    }
}
