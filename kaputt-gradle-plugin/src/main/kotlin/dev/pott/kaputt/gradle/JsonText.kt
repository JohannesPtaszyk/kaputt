package dev.pott.kaputt.gradle

/** Appends pre-rendered array elements, comma-separated; an empty array stays empty. */
internal fun StringBuilder.appendJsonRows(rows: List<String>) {
    if (rows.isNotEmpty()) appendLine(rows.joinToString(separator = ",\n"))
}

internal object JsonText {
    fun quote(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> {
                        append("\\\"")
                    }

                    '\\' -> {
                        append("\\\\")
                    }

                    '\n' -> {
                        append("\\n")
                    }

                    '\r' -> {
                        append("\\r")
                    }

                    '\t' -> {
                        append("\\t")
                    }

                    else -> {
                        if (char.code < 0x20) {
                            append("\\u%04x".format(char.code))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
}

/**
 * Entity escaping shared by the HTML and PIT-XML reports. `&#39;` is a numeric
 * reference, so it is valid in both, unlike the XML-only `&apos;`.
 */
internal object MarkupText {
    fun escape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
}
