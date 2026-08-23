package dev.pott.kaputt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse

class MutationControlTest {
    @Test
    fun `GIVEN no mutation property WHEN isMutationActive THEN false`() {
        assertFalse(isMutationActive("abc123"))
    }
}
