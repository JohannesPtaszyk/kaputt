package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import dev.pott.kaputt.runtime.MutationTestRunner
import java.util.Locale

enum class MutantStatus { KILLED, TIMED_OUT, SURVIVED, IGNORED, ERROR }

data class MutantResult(
    val point: MutationPoint,
    val status: MutantStatus,
    val durationMs: Long,
)

object MutationScore {
    fun classify(
        kind: TargetRun.TargetKind,
        exitCode: Int?,
        timedOut: Boolean,
    ): MutantStatus =
        when {
            timedOut -> {
                MutantStatus.TIMED_OUT
            }

            exitCode == 0 -> {
                MutantStatus.SURVIVED
            }

            exitCode == null -> {
                MutantStatus.ERROR
            }

            kind == TargetRun.TargetKind.ANDROID_DEVICE &&
                exitCode == AndroidInstrumentation.EXIT_INSTRUMENTATION_ERROR -> {
                MutantStatus.ERROR
            }

            kind == TargetRun.TargetKind.JVM &&
                (
                    exitCode == MutationTestRunner.EXIT_INTERNAL_ERROR ||
                        exitCode == MutationTestRunner.EXIT_NO_TESTS
                ) -> {
                MutantStatus.ERROR
            }

            else -> {
                MutantStatus.KILLED
            }
        }

    /**
     * A mutant whose tests already reported a failure is detected, even when
     * the fork then hangs: mutations that break cleanup often deadlock a later
     * test, and calling that a timeout hides the kill that came first.
     */
    fun classify(
        kind: TargetRun.TargetKind,
        exitCode: Int?,
        timedOut: Boolean,
        log: String?,
    ): MutantStatus {
        val status = classify(kind, exitCode, timedOut)
        val detected = log != null && MutationTestRunner.FAILURE_PREFIX in log
        return if (status == MutantStatus.TIMED_OUT && detected) MutantStatus.KILLED else status
    }

    /**
     * Combines the per-target verdicts for one mutant: detected anywhere is
     * detected, and a mutant only counts as survived when every target that
     * contains it ran cleanly and stayed green.
     */
    fun combine(statuses: List<MutantStatus>): MutantStatus =
        when {
            MutantStatus.KILLED in statuses -> MutantStatus.KILLED
            MutantStatus.TIMED_OUT in statuses -> MutantStatus.TIMED_OUT
            MutantStatus.ERROR in statuses -> MutantStatus.ERROR
            else -> MutantStatus.SURVIVED
        }

    /**
     * Percentage of detected mutants (killed or timed out) among all mutants
     * that ran cleanly; baselined (IGNORED) mutants stay out of the score.
     * 100.0 for an empty run.
     */
    fun score(results: List<MutantResult>): Double {
        val measurable =
            results.count {
                it.status != MutantStatus.ERROR && it.status != MutantStatus.IGNORED
            }
        if (measurable == 0) return 100.0
        val detected =
            results.count {
                it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMED_OUT
            }
        return detected * 100.0 / measurable
    }

    /** The one rendering of a score all reports and threshold messages share. */
    fun format(score: Double): String = String.format(Locale.ROOT, "%.1f", score)
}
