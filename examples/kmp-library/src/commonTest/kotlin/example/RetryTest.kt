package example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryTest {

    @Test
    fun `backs off further on each attempt`() {
        assertEquals(100, Retry.delayFor(attempt = 0))
        assertEquals(300, Retry.delayFor(attempt = 2))
    }

    @Test
    fun `retries a failure early on`() {
        assertTrue(Retry.shouldRetry(attempt = 1, failed = true))
    }

    @Test
    fun `never retries a success`() {
        assertFalse(Retry.shouldRetry(attempt = 1, failed = false))
    }

    // The attempt limit itself is never tested, so the mutant that turns
    // `<` into `<=` survives on every target.
}
