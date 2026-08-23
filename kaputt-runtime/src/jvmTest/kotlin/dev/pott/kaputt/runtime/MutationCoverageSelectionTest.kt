package dev.pott.kaputt.runtime

import org.junit.runner.Description
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationCoverageSelectionTest {
    private val wanted = setOf("com.example.ScoreTest#boundary")

    @Test
    fun `GIVEN a covered test WHEN selecting THEN it runs`() {
        val test = Description.createTestDescription("com.example.ScoreTest", "boundary")

        assertTrue(JUnit4Driver.coverageSelects(test, wanted))
    }

    @Test
    fun `GIVEN an uncovered test in the same class WHEN selecting THEN it is skipped`() {
        val test = Description.createTestDescription("com.example.ScoreTest", "unrelated")

        assertFalse(JUnit4Driver.coverageSelects(test, wanted))
    }

    @Test
    fun `GIVEN a suite holding one covered test WHEN selecting THEN the suite runs`() {
        val suite =
            Description.createSuiteDescription("com.example.ScoreTest").apply {
                addChild(Description.createTestDescription("com.example.ScoreTest", "unrelated"))
                addChild(Description.createTestDescription("com.example.ScoreTest", "boundary"))
            }

        assertTrue(JUnit4Driver.coverageSelects(suite, wanted))
    }

    @Test
    fun `GIVEN a suite with no covered test WHEN selecting THEN the suite is skipped`() {
        val suite =
            Description.createSuiteDescription("com.example.OtherTest").apply {
                addChild(Description.createTestDescription("com.example.OtherTest", "somethingElse"))
            }

        assertFalse(JUnit4Driver.coverageSelects(suite, wanted))
    }

    @Test
    fun `GIVEN nothing covered WHEN selecting THEN no test runs`() {
        val test = Description.createTestDescription("com.example.ScoreTest", "boundary")

        assertFalse(JUnit4Driver.coverageSelects(test, emptySet()))
    }
}
