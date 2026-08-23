package example

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Only compiled into the paid variants. It checks a device count well inside
 * the allowance and one well outside, never the allowance itself, so the
 * boundary mutant survives.
 */
class DeviceLimitTest {

    @Test
    fun `allows a handful of devices`() {
        assertFalse(DeviceLimit.overLimit(devices = 3))
    }

    @Test
    fun `rejects far too many devices`() {
        assertTrue(DeviceLimit.overLimit(devices = 50))
    }
}
