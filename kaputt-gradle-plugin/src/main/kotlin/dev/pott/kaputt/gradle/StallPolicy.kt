package dev.pott.kaputt.gradle

import java.util.concurrent.TimeUnit

/**
 * Decides when a fork has stopped making progress. Mutants that break a
 * loop-termination guard keep the process busy but stop finishing tests, and
 * unlike a duration cap this reads that directly, so machine load and the
 * number of tests a mutant runs do not affect the verdict.
 */
object StallPolicy {
    enum class Verdict { RUNNING, STALLED, EXPIRED }

    fun evaluate(
        elapsedNanos: Long,
        sinceProgressNanos: Long,
        timeoutSeconds: Long,
        stallSeconds: Long,
    ): Verdict {
        val stallNanos = TimeUnit.SECONDS.toNanos(stallSeconds)
        return when {
            elapsedNanos > TimeUnit.SECONDS.toNanos(timeoutSeconds) -> Verdict.EXPIRED

            // Loading a large test classpath reports no progress and must not
            // read as a hang, so only the cap applies during start-up.
            elapsedNanos <= stallNanos -> Verdict.RUNNING

            stallSeconds > 0 && sinceProgressNanos > stallNanos -> Verdict.STALLED

            else -> Verdict.RUNNING
        }
    }
}
