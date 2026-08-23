package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint

/**
 * Textual preview of a mutant for the diff view. Mutations happen at the IR
 * level, so the "after" line is reconstructed from the operator, the recorded
 * source span and the mutation description, which is faithful for every
 * operator this plugin ships.
 */
object MutantDiffPreview {
    data class Diff(
        val before: String,
        val after: String,
    )

    /** Returns null when the span cannot be mapped onto the line. */
    fun diff(
        point: MutationPoint,
        line: String,
    ): Diff? {
        if (point.column !in 1..line.length + 1) return null
        val start = point.column - 1
        val end =
            if (point.endLine == point.line &&
                point.endColumn in point.column..line.length + 1
            ) {
                point.endColumn - 1
            } else {
                line.length
            }
        if (start >= end) return null
        val span = line.substring(start, end)
        val mutatedSpan = mutateSpan(point, span) ?: return null
        val after = line.substring(0, start) + mutatedSpan + line.substring(end)
        return Diff(before = line, after = after)
    }

    private fun mutateSpan(
        point: MutationPoint,
        span: String,
    ): String? {
        val replacement = REPLACED_PATTERN.matchEntire(point.description)
        return when {
            replacement != null -> {
                val (from, to) = replacement.destructured
                if (from in span) span.replaceFirst(from, to) else null
            }

            point.description == "removed '!'" -> {
                if (span.startsWith("!")) {
                    span.removePrefix("!")
                } else {
                    span.removeSuffix(".not()").takeIf { it != span }
                }
            }

            point.description.startsWith("removed call to") -> {
                ""
            }

            NEGATED_PATTERN.matches(point.description) -> {
                "!($span)"
            }

            point.description.startsWith(RETURN_PREFIX) -> {
                point.description.removePrefix(RETURN_PREFIX)
            }

            else -> {
                null
            }
        }
    }

    private val REPLACED_PATTERN = Regex("replaced '(.+)' with '(.+)'")
    private val NEGATED_PATTERN = Regex("negated '.+'")
    private const val RETURN_PREFIX = "replaced return value with "
}
