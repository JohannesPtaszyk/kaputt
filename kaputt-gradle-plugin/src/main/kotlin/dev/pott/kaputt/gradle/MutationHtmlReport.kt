package dev.pott.kaputt.gradle

/**
 * Self-contained HTML report: summary tiles, a score meter, the survivors
 * table first (the actionable part), then all mutants grouped per file.
 * Status is always carried by a colored dot plus a text label, never color
 * alone; light/dark follow the OS preference.
 */
object MutationHtmlReport {
    fun render(
        module: String,
        results: List<MutantResult>,
        sourceLookup: (String) -> List<String>? = { null },
    ): String {
        val scoreText = MutationScore.format(MutationScore.score(results))
        val counts = results.groupingBy { it.status }.eachCount()
        val survivors = results.filter { it.status == MutantStatus.SURVIVED }
        val byFile = results.groupBy { it.point.filePath }
        // Survivors are rendered twice, in their own table and in the per-file
        // section, and every diff re-reads and re-diffs the source line.
        val cellCache = HashMap<MutantResult, String>()
        val cell: (MutantResult) -> String = { result ->
            cellCache.getOrPut(result) { mutationCell(result, sourceLookup) }
        }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>Mutation report: ${escape(module)}</title>")
            appendLine("<style>$STYLE</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<main>")
            appendLine("<h1>Mutation report <code>${escape(module)}</code></h1>")

            val ranked = SurvivorRanking.group(results)
            val highSignal = ranked.count { SurvivorRanking.isHighSignal(it.first) }
            appendLine("<section class=\"tiles\">")
            appendTile("Untested behavior", highSignal.toString(), hero = true)
            appendTile("Other survivors", (ranked.size - highSignal).toString())
            appendTile("Score", "$scoreText%")
            appendTile("Mutants", results.size.toString())
            appendTile("Killed", counts.count(MutantStatus.KILLED))
            appendTile("Timed out", counts.count(MutantStatus.TIMED_OUT))
            appendTile("Survived", counts.count(MutantStatus.SURVIVED))
            appendTile("Ignored", counts.count(MutantStatus.IGNORED))
            appendTile("Errors", counts.count(MutantStatus.ERROR))
            appendLine("</section>")

            appendLine(
                "<div class=\"meter\" role=\"img\" aria-label=\"Mutation score $scoreText percent\">",
            )
            appendLine("<div class=\"meter-fill\" style=\"width:$scoreText%\"></div>")
            appendLine("</div>")

            appendLine("<h2>By mutation kind <span class=\"muted\">weakest first</span></h2>")
            appendLine("<table>")
            appendLine(
                "<thead><tr><th>Mutation</th><th>Mutants</th><th>Survived</th>" +
                    "<th>Score</th></tr></thead><tbody>",
            )
            MutationBreakdown.byOperator(results).forEach { row ->
                appendLine(
                    "<tr><td>${escape(row.operator)}</td>" +
                        "<td class=\"num\">${row.mutants}</td>" +
                        "<td class=\"num\">${row.survived}</td>" +
                        "<td class=\"num\">${MutationScore.format(row.score)}%</td></tr>",
                )
            }
            appendLine("</tbody></table>")
            val worst = SurvivorRanking.worstFiles(results)
            if (worst.isNotEmpty()) {
                appendLine("<h2>Worst files</h2>")
                appendLine("<ul class=\"worst\">")
                worst.forEach { (file, count) ->
                    appendLine("<li><code>${escape(file)}</code> $count</li>")
                }
                appendLine("</ul>")
            }
            if (ranked.isNotEmpty()) {
                appendLine("<h2>Survivors <span class=\"muted\">most actionable first</span></h2>")
                appendGroupedTable(ranked, cell)
            }

            appendLine("<h2>All mutants by file</h2>")
            byFile.entries
                .sortedByDescending { (_, fileResults) ->
                    fileResults.count { it.status == MutantStatus.SURVIVED }
                }.forEach { (filePath, fileResults) ->
                    val surviving = fileResults.count { it.status == MutantStatus.SURVIVED }
                    appendLine("<details${if (surviving > 0) " open" else ""}>")
                    appendLine(
                        "<summary><code>${escape(filePath.substringAfterLast('/'))}</code> " +
                            "<span class=\"muted\">${fileResults.size} mutants, " +
                            "$surviving survived</span></summary>",
                    )
                    appendMutantTable(fileResults.sortedBy { it.point.line }, cell)
                    appendLine("</details>")
                }

            appendLine("</main>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun Map<MutantStatus, Int>.count(status: MutantStatus): String = (this[status] ?: 0).toString()

    private fun StringBuilder.appendTile(
        label: String,
        value: String,
        hero: Boolean = false,
    ) {
        appendLine("<div class=\"tile${if (hero) " hero" else ""}\">")
        appendLine("<div class=\"tile-value\">${escape(value)}</div>")
        appendLine("<div class=\"tile-label\">${escape(label)}</div>")
        appendLine("</div>")
    }

    private fun StringBuilder.appendGroupedTable(
        rows: List<SurvivorRanking.Group>,
        cell: (MutantResult) -> String,
    ) {
        appendLine("<table>")
        appendLine(
            "<thead><tr><th>File</th><th>Line</th><th>Function</th>" +
                "<th>Operator</th><th>Mutation</th><th>Count</th></tr></thead>",
        )
        appendLine("<tbody>")
        rows.forEach { (result, count) ->
            val signal = if (SurvivorRanking.isHighSignal(result)) " class=\"signal\"" else ""
            appendLine(
                "<tr$signal>" +
                    "<td><code>${escape(
                        result.point.filePath.substringAfterLast('/'),
                    )}</code></td>" +
                    "<td class=\"num\">${result.point.line}</td>" +
                    "<td><code>${escape(result.point.function)}</code></td>" +
                    "<td>${escape(result.point.operator)}</td>" +
                    "<td>${cell(result)}</td>" +
                    "<td class=\"num\">${if (count > 1) count.toString() else ""}</td>" +
                    "</tr>",
            )
        }
        appendLine("</tbody>")
        appendLine("</table>")
    }

    private fun StringBuilder.appendMutantTable(
        rows: List<MutantResult>,
        cell: (MutantResult) -> String,
        withFile: Boolean = false,
    ) {
        appendLine("<table>")
        appendLine(
            "<thead><tr><th>Status</th>" +
                (if (withFile) "<th>File</th>" else "") +
                "<th>Line</th><th>Function</th><th>Operator</th><th>Mutation</th></tr></thead>",
        )
        appendLine("<tbody>")
        rows.forEach { result ->
            val statusClass =
                result.status.name
                    .lowercase()
                    .replace('_', '-')
            val fileCell =
                if (withFile) {
                    "<td><code>${escape(result.point.filePath.substringAfterLast('/'))}</code></td>"
                } else {
                    ""
                }
            appendLine(
                "<tr>" +
                    "<td><span class=\"status $statusClass\">" +
                    "<span class=\"dot\" aria-hidden=\"true\"></span>" +
                    "${result.status.name}</span></td>" +
                    fileCell +
                    "<td class=\"num\">${result.point.line}</td>" +
                    "<td><code>${escape(result.point.function)}</code></td>" +
                    "<td>${escape(result.point.operator)}</td>" +
                    "<td>${cell(result)}</td>" +
                    "</tr>",
            )
        }
        appendLine("</tbody>")
        appendLine("</table>")
    }

    private fun mutationCell(
        result: MutantResult,
        sourceLookup: (String) -> List<String>?,
    ): String {
        val description = escape(result.point.description)
        val diff = renderDiff(result, sourceLookup) ?: return description
        return "<details class=\"diffbox\"><summary>$description</summary>$diff</details>"
    }

    private fun renderDiff(
        result: MutantResult,
        sourceLookup: (String) -> List<String>?,
    ): String? {
        val point = result.point
        val lines = sourceLookup(point.filePath) ?: return null
        val lineIndex = point.line - 1
        val original = lines.getOrNull(lineIndex) ?: return null
        val diff = MutantDiffPreview.diff(point, original) ?: return null

        val firstContext = (lineIndex - CONTEXT_LINES).coerceAtLeast(0)
        val lastContext = (lineIndex + CONTEXT_LINES).coerceAtMost(lines.lastIndex)
        val indent =
            (firstContext..lastContext)
                .map { index -> lines[index] }
                .filter(String::isNotBlank)
                .minOf { line -> line.takeWhile { it == ' ' }.length }
        return buildString {
            append("<pre class=\"diff\">")
            (firstContext..lastContext).forEach { index ->
                val number = index + 1
                if (index == lineIndex) {
                    append("<span class=\"chg\">±${lineNo(number)}")
                    append(inlineDiff(diff.before.dedent(indent), diff.after.dedent(indent)))
                    append("</span>\n")
                } else {
                    append(
                        "<span class=\"ctx\"> ${lineNo(
                            number,
                        )}${escape(lines[index].dedent(indent))}</span>\n",
                    )
                }
            }
            append("</pre>")
        }
    }

    /** Marks only the minimal changed region: common prefix/suffix stay plain. */
    private fun inlineDiff(
        before: String,
        after: String,
    ): String {
        var prefix = 0
        val maxPrefix = minOf(before.length, after.length)
        while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(before.length, after.length) - prefix
        while (suffix < maxSuffix &&
            before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
        ) {
            suffix++
        }
        val removed = before.substring(prefix, before.length - suffix)
        val inserted = after.substring(prefix, after.length - suffix)
        return buildString {
            append(escape(before.substring(0, prefix)))
            if (removed.isNotEmpty()) append("<del>${escape(removed)}</del>")
            if (inserted.isNotEmpty()) append("<ins>${escape(inserted)}</ins>")
            append(escape(before.substring(before.length - suffix)))
        }
    }

    private fun String.dedent(indent: Int): String = if (length >= indent && take(indent).all { it == ' ' }) substring(indent) else this

    private fun lineNo(number: Int): String = number.toString().padStart(4) + "  "

    private const val CONTEXT_LINES = 1

    internal fun escape(value: String): String = MarkupText.escape(value)

    // Status colors from the validated reference palette; on light surfaces the
    // dot never carries meaning alone, the text label sits next to it.
    private val STYLE =
        """
        :root {
          color-scheme: light dark;
          --surface: #fcfcfb; --surface-2: #f0efec;
          --ink: #0b0b0b; --ink-2: #52514e; --border: #dddcd8;
          --meter: #2a78d6;
          --good: #0ca30c; --warning: #fab219; --serious: #ec835a;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --surface: #1a1a19; --surface-2: #262624;
            --ink: #ffffff; --ink-2: #c3c2b7; --border: #383835;
            --meter: #3987e5;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0; background: var(--surface); color: var(--ink);
          font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif;
        }
        main { max-width: 72rem; margin: 0 auto; padding: 2rem 1.25rem 4rem; }
        h1 { font-size: 1.4rem; font-weight: 650; }
        h1 code { font-size: 1.15rem; }
        h2 { font-size: 1.05rem; margin-top: 2.2rem; }
        .muted { color: var(--ink-2); font-weight: 400; font-size: .9em; }
        .tiles { display: flex; flex-wrap: wrap; gap: .75rem; margin: 1.25rem 0 1rem; }
        .tile {
          background: var(--surface-2); border-radius: 10px;
          padding: .7rem 1.1rem; min-width: 6.5rem;
        }
        .tile-value { font-size: 1.35rem; font-weight: 650; font-variant-numeric: tabular-nums; }
        .tile.hero .tile-value { font-size: 1.9rem; }
        .tile-label { color: var(--ink-2); font-size: .8rem; }
        .meter {
          height: 8px; border-radius: 4px; background: var(--surface-2);
          overflow: hidden; margin-bottom: .5rem;
        }
        .meter-fill { height: 100%; border-radius: 4px; background: var(--meter); }
        table { border-collapse: collapse; width: 100%; margin: .6rem 0 1rem; }
        th {
          text-align: left; color: var(--ink-2); font-size: .78rem;
          text-transform: uppercase; letter-spacing: .04em; font-weight: 600;
        }
        th, td {
          padding: .4rem .6rem; border-bottom: 1px solid var(--border);
          vertical-align: top;
        }
        td.num { font-variant-numeric: tabular-nums; text-align: right; }
        code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .88em; }
        details { margin: .6rem 0; }
        summary { cursor: pointer; padding: .35rem 0; }
        .status { display: inline-flex; align-items: center; gap: .4rem; font-size: .82rem; font-weight: 600; }
        .dot { width: 9px; height: 9px; border-radius: 50%; background: var(--ink-2); }
        .status.killed .dot, .status.timed-out .dot { background: var(--good); }
        .status.survived .dot { background: var(--serious); }
        .status.error .dot { background: var(--warning); }
        tr.signal td:first-child { border-left: 3px solid var(--serious); }
        ul.worst { margin: .4rem 0 1rem; padding-left: 1.2rem; }
        ul.worst li { margin: .15rem 0; }
        details.diffbox summary {
          padding: 0; list-style-position: outside; color: inherit;
        }
        pre.diff {
          margin: .4rem 0 .2rem; padding: .3rem 0; border-radius: 6px;
          background: var(--surface-2); overflow-x: auto; display: inline-block;
          min-width: min(100%, 34rem); max-width: 100%;
          font: .75rem/1.45 ui-monospace, SFMono-Regular, Menlo, monospace;
        }
        pre.diff span { display: block; padding: 0 .6rem; white-space: pre; }
        pre.diff .ctx { color: var(--ink-2); }
        pre.diff del {
          background: color-mix(in srgb, var(--serious) 24%, transparent);
          text-decoration: line-through; text-decoration-thickness: 1px;
          border-radius: 2px; color: inherit;
        }
        pre.diff ins {
          background: color-mix(in srgb, var(--good) 22%, transparent);
          text-decoration: none; border-radius: 2px;
        }
        """.trimIndent()
}
