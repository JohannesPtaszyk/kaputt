package example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncTest {

    @Test
    fun `backs off further after each failure`() {
        assertEquals(10, Sync.backoffMinutes(consecutiveFailures = 2))
    }

    @Test
    fun `syncs when something is pending`() {
        assertTrue(Sync.maySync(pendingChanges = 3))
    }

    @Test
    fun `stays idle with nothing pending`() {
        assertFalse(Sync.maySync(pendingChanges = 0))
    }
}
