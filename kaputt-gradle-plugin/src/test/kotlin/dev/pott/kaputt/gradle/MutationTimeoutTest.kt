package dev.pott.kaputt.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationTimeoutTest {
    @Test
    fun `GIVEN a slow baseline WHEN deriving THEN it scales by the factor`() {
        assertEquals(150, MutationTimeout.derive(baselineMs = 10_000, factor = 15.0))
    }

    @Test
    fun `GIVEN a fast suite WHEN deriving THEN the floor wins`() {
        assertEquals(
            MutationTimeout.MIN_SECONDS,
            MutationTimeout.derive(baselineMs = 200, factor = 15.0),
        )
    }

    @Test
    fun `GIVEN the slowest measured legitimate mutant THEN the derived timeout still covers it`() {
        val slowestLegitimateMs = 16_407L

        val derived = MutationTimeout.derive(baselineMs = 1734, factor = 15.0)

        assertTrue(derived * 1000 > slowestLegitimateMs)
    }

    @Test
    fun `GIVEN a custom factor WHEN deriving THEN it is honoured`() {
        assertEquals(300, MutationTimeout.derive(baselineMs = 10_000, factor = 30.0))
    }
}
