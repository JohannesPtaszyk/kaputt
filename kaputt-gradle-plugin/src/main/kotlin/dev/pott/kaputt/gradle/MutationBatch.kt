package dev.pott.kaputt.gradle

import dev.pott.kaputt.runtime.MutationTestRunner

/**
 * Groups mutants so that one JVM verifies many of them. Starting a fork and
 * loading the test classpath costs far more than running the few tests that
 * cover a mutation point, so batching is what makes per-mutant cost approach
 * the cost of the tests themselves.
 */
object MutationBatch {
    data class Entry(
        val id: String,
        val tests: List<String>,
    )

    /**
     * Mutants sharing this key need the same test classes, so putting them in
     * one fork pays the per-class setup once instead of once per fork.
     */
    fun groupKey(tests: Collection<String>): String =
        tests
            .map { it.substringBefore('#') }
            .distinct()
            .sorted()
            .joinToString(separator = ",")

    fun fileContent(entries: List<Entry>): String =
        entries.joinToString(separator = "\n") { entry ->
            "${entry.id}\t${entry.tests.joinToString(separator = ",")}"
        }

    /** Verdicts the fork reported, by mutation id; a missing id never ran. */
    fun parseVerdicts(output: String): Map<String, Int> =
        output
            .lineSequence()
            .filter { it.startsWith(MutationTestRunner.VERDICT_PREFIX) }
            .mapNotNull { line ->
                val rest = line.removePrefix(MutationTestRunner.VERDICT_PREFIX).trim()
                val id = rest.substringBefore(' ')
                val code = rest.substringAfter(' ', "").trim().toIntOrNull()
                if (id.isNotEmpty() && code != null) id to code else null
            }.toMap()

    /**
     * Output the fork produced after the last mutant it reported a verdict
     * for, which is what the mutant it died on had to say for itself.
     */
    fun tailAfterLastVerdict(log: String): String {
        val last = log.lastIndexOf(MutationTestRunner.VERDICT_PREFIX)
        if (last < 0) return log
        val lineEnd = log.indexOf('\n', last)
        return if (lineEnd < 0) "" else log.substring(lineEnd + 1)
    }
}
