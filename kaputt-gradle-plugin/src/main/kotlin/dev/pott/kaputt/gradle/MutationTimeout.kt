package dev.pott.kaputt.gradle

/**
 * Derives the per-mutant timeout from the measured baseline.
 *
 * Mutants run far slower than a solo baseline because many share the machine,
 * measured at 2.5x for the median and 9.5x for the slowest legitimate mutant,
 * so the factor is deliberately generous. A timeout counts as detected, so
 * cutting a merely slow mutant short would silently inflate the score.
 */
object MutationTimeout {
    /** Floor for very fast suites, where a scaled baseline would be seconds. */
    const val MIN_SECONDS = 30L

    fun derive(
        baselineMs: Long,
        factor: Double,
    ): Long = maxOf(MIN_SECONDS, (baselineMs / MILLIS_PER_SECOND * factor).toLong())

    private const val MILLIS_PER_SECOND = 1000.0
}
