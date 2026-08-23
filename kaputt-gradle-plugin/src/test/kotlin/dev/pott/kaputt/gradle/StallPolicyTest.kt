package dev.pott.kaputt.gradle

import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

class StallPolicyTest {
    @Test
    fun `GIVEN a slow but progressing fork WHEN evaluating THEN it keeps running`() {
        assertEquals(
            StallPolicy.Verdict.RUNNING,
            StallPolicy.evaluate(SECONDS.toNanos(120), SECONDS.toNanos(5), 191, 30),
        )
    }

    @Test
    fun `GIVEN no finished test for the stall window WHEN evaluating THEN it is stalled`() {
        assertEquals(
            StallPolicy.Verdict.STALLED,
            StallPolicy.evaluate(SECONDS.toNanos(35), SECONDS.toNanos(31), 191, 30),
        )
    }

    @Test
    fun `GIVEN a fork past the absolute cap WHEN evaluating THEN it expires`() {
        assertEquals(
            StallPolicy.Verdict.EXPIRED,
            StallPolicy.evaluate(SECONDS.toNanos(200), SECONDS.toNanos(1), 191, 30),
        )
    }

    @Test
    fun `GIVEN a fork still starting up WHEN evaluating THEN it is not called stalled`() {
        assertEquals(
            StallPolicy.Verdict.RUNNING,
            StallPolicy.evaluate(SECONDS.toNanos(25), SECONDS.toNanos(25), 191, 30),
        )
    }

    @Test
    fun `GIVEN stall detection disabled WHEN evaluating THEN only the cap applies`() {
        assertEquals(
            StallPolicy.Verdict.RUNNING,
            StallPolicy.evaluate(SECONDS.toNanos(120), SECONDS.toNanos(90), 191, 0),
        )
    }
}
