package dev.pott.kaputt.compiler

/**
 * Glob-based include/exclude filter. Patterns are matched against both the
 * source file path and `package.FileName.kt`, so `**Serializer*`,
 * `com.example.shared.*` and `*TimeZoneSerializer.kt` all work.
 */
class MutationTargetFilter(
    includes: List<String>,
    excludes: List<String>,
) {
    private val includeRegexes = includes.map(::globToRegex)
    private val excludeRegexes = excludes.map(::globToRegex)

    fun matches(
        filePath: String,
        packageAndFileName: String,
    ): Boolean {
        val candidates = listOf(filePath.replace('\\', '/'), packageAndFileName)
        val included =
            includeRegexes.isEmpty() ||
                includeRegexes.any { regex -> candidates.any(regex::matches) }
        val excluded = excludeRegexes.any { regex -> candidates.any(regex::matches) }
        return included && !excluded
    }

    companion object {
        fun parsePatterns(raw: String?): List<String> =
            raw
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)

        private fun globToRegex(pattern: String): Regex {
            val regex =
                buildString {
                    pattern.forEach { char ->
                        when (char) {
                            '*' -> append(".*")
                            '?' -> append('.')
                            else -> append(Regex.escape(char.toString()))
                        }
                    }
                }
            return Regex(regex)
        }
    }
}
