package dev.pott.kaputt.compiler

import java.net.URLDecoder
import java.net.URLEncoder

data class MutationPoint(
    val id: String,
    val operator: String,
    val filePath: String,
    val line: Int,
    val function: String,
    val description: String,
    val column: Int = 0,
    val endLine: Int = 0,
    val endColumn: Int = 0,
)

/**
 * Line-oriented, dependency-free wire format between the compiler plugin and
 * the Gradle task: one record per line, tab-separated, URL-encoded fields.
 */
private object TsvLines {
    fun encode(rows: List<List<String>>): String =
        rows.joinToString(separator = "\n") { fields ->
            fields.joinToString(separator = "\t") { URLEncoder.encode(it, Charsets.UTF_8) }
        }

    fun decode(
        content: String,
        fieldCount: Int,
    ): List<List<String>> =
        content
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val fields = line.split('\t').map { URLDecoder.decode(it, Charsets.UTF_8) }
                require(fields.size == fieldCount) { "Malformed line: $line" }
                fields
            }.toList()
}

object MutationPointCodec {
    fun encode(points: List<MutationPoint>): String =
        TsvLines.encode(
            points.map { point ->
                listOf(
                    point.id,
                    point.operator,
                    point.filePath,
                    point.line.toString(),
                    point.function,
                    point.description,
                    point.column.toString(),
                    point.endLine.toString(),
                    point.endColumn.toString(),
                )
            },
        )

    fun decode(content: String): List<MutationPoint> =
        TsvLines.decode(content, FIELD_COUNT).map { fields ->
            MutationPoint(
                id = fields[0],
                operator = fields[1],
                filePath = fields[2],
                line = fields[3].toInt(),
                function = fields[4],
                description = fields[5],
                column = fields[6].toInt(),
                endLine = fields[7].toInt(),
                endColumn = fields[8].toInt(),
            )
        }

    private const val FIELD_COUNT = 9
}

/** Wire format for functions whose mutations were capped by the size guard. */
object SkippedFunctionCodec {
    fun encode(skipped: List<MutationTransformer.SkippedFunction>): String =
        TsvLines.encode(
            skipped.map { listOf(it.filePath, it.function, it.skipped.toString()) },
        )

    fun decode(content: String): List<MutationTransformer.SkippedFunction> =
        TsvLines.decode(content, FIELD_COUNT).map { fields ->
            MutationTransformer.SkippedFunction(
                filePath = fields[0],
                function = fields[1],
                skipped = fields[2].toInt(),
            )
        }

    private const val FIELD_COUNT = 3
}
