package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class MutationScoreTest {
    @Test
    fun `GIVEN jvm exit codes WHEN classify THEN statuses map like PIT`() {
        val jvm = TargetRun.TargetKind.JVM
        assertEquals(
            MutantStatus.SURVIVED,
            MutationScore.classify(jvm, exitCode = 0, timedOut = false),
        )
        assertEquals(
            MutantStatus.KILLED,
            MutationScore.classify(jvm, exitCode = 1, timedOut = false),
        )
        assertEquals(
            MutantStatus.KILLED,
            MutationScore.classify(jvm, exitCode = 134, timedOut = false),
        )
        assertEquals(
            MutantStatus.TIMED_OUT,
            MutationScore.classify(jvm, exitCode = null, timedOut = true),
        )
        assertEquals(
            MutantStatus.ERROR,
            MutationScore.classify(jvm, exitCode = 2, timedOut = false),
        )
        assertEquals(
            MutantStatus.ERROR,
            MutationScore.classify(jvm, exitCode = 3, timedOut = false),
        )
        assertEquals(
            MutantStatus.ERROR,
            MutationScore.classify(jvm, exitCode = null, timedOut = false),
        )
    }

    @Test
    fun `GIVEN native exit codes WHEN classify THEN any failure is a kill`() {
        val native = TargetRun.TargetKind.NATIVE
        assertEquals(
            MutantStatus.SURVIVED,
            MutationScore.classify(native, exitCode = 0, timedOut = false),
        )
        assertEquals(
            MutantStatus.KILLED,
            MutationScore.classify(native, exitCode = 1, timedOut = false),
        )
        assertEquals(
            MutantStatus.KILLED,
            MutationScore.classify(native, exitCode = 2, timedOut = false),
        )
        assertEquals(
            MutantStatus.TIMED_OUT,
            MutationScore.classify(native, exitCode = null, timedOut = true),
        )
    }

    @Test
    fun `GIVEN per-target statuses WHEN combine THEN detection anywhere wins`() {
        assertEquals(
            MutantStatus.KILLED,
            MutationScore.combine(listOf(MutantStatus.SURVIVED, MutantStatus.KILLED)),
        )
        assertEquals(
            MutantStatus.TIMED_OUT,
            MutationScore.combine(listOf(MutantStatus.TIMED_OUT, MutantStatus.SURVIVED)),
        )
        assertEquals(
            MutantStatus.ERROR,
            MutationScore.combine(listOf(MutantStatus.SURVIVED, MutantStatus.ERROR)),
        )
        assertEquals(
            MutantStatus.SURVIVED,
            MutationScore.combine(listOf(MutantStatus.SURVIVED, MutantStatus.SURVIVED)),
        )
    }

    @Test
    fun `GIVEN mixed results WHEN score THEN errors and baselined excluded from denominator`() {
        val results =
            listOf(
                result(MutantStatus.KILLED),
                result(MutantStatus.TIMED_OUT),
                result(MutantStatus.SURVIVED),
                result(MutantStatus.SURVIVED),
                result(MutantStatus.IGNORED),
                result(MutantStatus.ERROR),
            )

        assertEquals(50.0, MutationScore.score(results))
    }

    @Test
    fun `GIVEN no results WHEN score THEN full score`() {
        assertEquals(100.0, MutationScore.score(emptyList()))
    }

    private fun result(status: MutantStatus) =
        MutantResult(
            point =
                MutationPoint(
                    id = "0123456789ab",
                    operator = "math",
                    filePath = "/src/Foo.kt",
                    line = 1,
                    function = "foo",
                    description = "replaced '+' with '-'",
                ),
            status = status,
            durationMs = 10,
        )

    @Test
    fun `GIVEN a hang after a reported failure WHEN classify THEN the mutant is killed`() {
        val status =
            MutationScore.classify(
                kind = TargetRun.TargetKind.JVM,
                exitCode = null,
                timedOut = true,
                log = "..\nFAILED: closesInOrder(): expected:<false> but was:<true>\n.",
            )

        assertEquals(MutantStatus.KILLED, status)
    }

    @Test
    fun `GIVEN a hang with no reported failure WHEN classify THEN it stays a timeout`() {
        val status =
            MutationScore.classify(
                kind = TargetRun.TargetKind.JVM,
                exitCode = null,
                timedOut = true,
                log = "......",
            )

        assertEquals(MutantStatus.TIMED_OUT, status)
    }
}
