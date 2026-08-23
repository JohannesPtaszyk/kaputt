# How it works

## Mutant schemata

Every mutation is compiled into the code once, wrapped in a guard:

```kotlin
// what you wrote
if (score > threshold) award()

// what the instrumented build contains
if (if (isMutationActive("a1b2")) score >= threshold else score > threshold) award()
```

The module compiles once and carries every mutant at the same time. Only the
one named by the `KOTLIN_MUTATION` environment variable behaves differently, so
verifying a mutant is a fresh test process rather than another compile.

Instrumentation happens in Kotlin IR, before the platform backends run. That is
why one pass covers jvm and native, and why a mutant in `commonMain` gets the
same id on every target.

## A run, start to finish

1. The instrumented compile writes every mutation point to
   `build/mutation/<target>/mutations.tsv`.
2. A baseline runs the suite once per selected target with no mutant active. If
   it is not green, the run stops: verdicts against a red suite mean nothing.
   The baseline also records which tests reach each mutation point, and its
   duration sets the per-mutant timeout.
3. Mutants run in forks, several per fork, each verified by the tests coverage
   attributed to it.
4. Verdicts are combined per mutant and written to the reports.

## Verdicts

- **Killed**: a test failed. The mutant was detected.
- **Timed out**: the mutant never finished, usually a broken loop guard. Counts
  as detected.
- **Survived**: every selected target ran green. The behavior is unverified.
- **Ignored**: listed in `mutation-baseline.txt` as an accepted equivalent
  mutant. Kept out of the score.

A mutant is killed if any target detects it, and survives only when every
target that contains it stayed green.

## Coverage filtering

The baseline records which tests execute each mutation point, and each mutant
then runs only those tests. A mutant no test reaches is reported as survived
without being run, since nothing could detect it.

Tests whose runner loads the code in its own classloader, Robolectric among
them, execute out of sight of this recording. Their mutants would be filtered
away as unreachable and reported as survived without running, so a run that
sees such a runner says so and asks for `coverageFiltering.set(false)`.

This also assumes the suite covers the same code on every run. A path that only
executes on some timings can be missed, and its mutants are reported as
survived rather than killed. The filter can skip a mutant into survived; it
can never turn one into killed. Set `coverageFiltering.set(false)` for an
exact, slower run.

## Hangs

A mutant that breaks a loop guard never finishes. Forks are killed on lack of
progress rather than elapsed time: a fork that finishes no test for
`stallSeconds` is scored as detected. Duration alone predicts badly, because a
legitimate mutant can run far under the baseline while machine load stretches
another well past it.

## What is never mutated

Generated code, `const val` and delegated-property initializers, compound
assignments, `++` and `--`, `inline` functions, `@Composable` functions, and
the structural constants of desugared `?:`, `?.`, `&&` and `||`. The Kotlin
backends rewrite those shapes and assert on them, so a guard inside one is
invalid IR rather than a mutant.

## Runaway mutants

Flipping a comparison or an arithmetic operator in a loop can remove the
condition that ends it. The compile puts a deadline check at the head of every
loop body, so such a mutant ends its own run from the inside instead of hanging
until the process is killed: the verdict says what happened, and the fork lives
to verify the rest of its batch. The deadline matches the per-mutant timeout
and only runs while a mutant is active.

Blocking waits are a different shape. A mutant that removes a release or a
close can deadlock a later test, which no loop check can see; those still end
on the process timeout, and count as detected if the tests had already failed.
