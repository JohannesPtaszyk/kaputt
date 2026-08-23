# Reading a report

Each run writes to `build/reports/mutation-test/`.

| File | Use |
| --- | --- |
| `mutation-report.html` | The one to open. Tiles, per-operator breakdown, survivors, per-mutant diff |
| `mutations.xml` | PIT format, read by the SonarQube mutation-analysis plugin |
| `mutation-report.sarif` | Survivors as warnings for GitHub code scanning |
| `stryker-report.json` | Stryker schema, for its dashboard and elements viewer |
| `mutation-report.md` | Drop into a pull request or a CI step summary |
| `mutation-report.json` | Scripting |
| `logs/` | One process log per fork, plus the baseline |

## Start with untested behavior

The report leads with the number of survivors from operators that change
control flow, not with the score. Those are the rows worth reading. Survivors
repeated at the same place are collapsed to a count, and the worst files are
listed above the table.

The score is there for trend, not as a target. A module full of data classes
will sit low no matter how good its tests are.

## The diff view

Every mutation expands to show the surrounding source, the original line with
the mutated region marked, and the line as the mutant sees it. That is usually
enough to decide whether the survivor is a missing assertion or an equivalent
mutant.

## Acting on a survivor

Read it as a claim about your suite: "nothing fails when this changes". If the
claim is wrong, the test to add is the one that fails against the mutant. If
the claim is fine because the change cannot matter, exclude it or record it in
the baseline instead of writing a contrived test.
