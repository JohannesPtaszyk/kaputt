package dev.pott.kaputt.gradle

/**
 * The checked-in list of accepted (equivalent) surviving mutants: survivors
 * whose id appears in it are reported as ignored and stay out of the score.
 */
object MutationBaseline {
    fun parse(text: String): Set<String> =
        text
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    fun render(survivorIds: List<String>): String =
        buildString {
            appendLine("# Accepted equivalent mutants, one id per line.")
            appendLine("# Regenerate with -Pmutation.updateBaseline=true.")
            survivorIds.forEach(::appendLine)
        }

    fun apply(
        results: List<MutantResult>,
        accepted: Set<String>,
    ): List<MutantResult> =
        results.map { result ->
            if (result.status == MutantStatus.SURVIVED && result.point.id in accepted) {
                result.copy(status = MutantStatus.IGNORED)
            } else {
                result
            }
        }
}
