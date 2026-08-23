# kaputt

Mutation testing for Kotlin: kaputt breaks your code on purpose — if the tests
stay green, that's the finding. (German: *kaputt*, broken.)

It generates small behavioral changes (mutants) in Kotlin code and runs the
test suite against each one. A test that fails kills the mutant. A mutant that
survives marks logic no test actually checks, which is what line coverage
cannot tell you.

Mutants are generated in Kotlin IR rather than bytecode, so one pass covers the
jvm and native targets of a multiplatform module. A mutant in `commonMain` gets
the same id on every target and counts as survived only when every selected
target stayed green.

Each mutant runs in a fresh test process with `KOTLIN_MUTATION` set to its id.
Instrumentation happens only in builds that invoke `mutationTest`; every other
build compiles untouched code.

Full documentation: <https://johannesptaszyk.github.io/kaputt/>

Runnable examples for JVM, Kotlin Multiplatform, Android, instrumented Android and Android with
flavours live in [`examples/`](examples).

## Quick start

Using a composite build (until artifacts are published):

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("path/to/kotlin-mutation-testing")
}
includeBuild("path/to/kotlin-mutation-testing")
```

```kotlin
// root build.gradle.kts, applies itself to all subprojects
plugins {
    id("dev.pott.kaputt")
}
```

```bash
# Verify a module's mutants with the jvm target's tests (default)
./gradlew :my:module:mutationTest

# Also verify against iOS simulator tests (macOS host)
./gradlew :my:module:mutationTest -Pmutation.targets=jvm,iosSimulatorArm64

# Narrow to files/packages while iterating
./gradlew :my:module:mutationTest -Pmutation.target="*ScoreCalculator*"
```

The task runs a green baseline first, then all mutants in parallel processes,
and writes to `build/reports/mutation-test/`:

| File | Purpose |
| --- | --- |
| `mutation-report.html` | Score tiles, survivors table, per-file sections, expandable per-mutant **diff view** with source context |
| `mutations.xml` | PIT report format, read by the SonarQube mutation-analysis plugin |
| `mutation-report.sarif` | Survivors as SARIF warnings for GitHub code scanning |
| `stryker-report.json` | Stryker report schema, read by the Stryker dashboard and elements viewer |
| `mutation-report.md` / `.json` | PR comments and scripting |
| `logs/` | One process log per mutant run |

## Mutation operators

| Key | Mutation |
| --- | --- |
| `math` | `+ ↔ -`, `* ↔ /`, `% → *` on primitive numbers |
| `conditional-boundary` | `< ↔ <=`, `> ↔ >=` |
| `negate-conditional` | `==`, `<`, `<=`, `>`, `>=` negated |
| `boolean-literal` | `true ↔ false` |
| `number-literal` | numeric literal `n → n + 1` and `n → n - 1` |
| `string-literal` | non-empty string literal → `""` |
| `remove-not` | `!x → x`, including the explicit `x.not()` spelling |
| `void-call-removal` | drops a statement-level `Unit` call |
| `safe-call` | `a?.b` → `a!!.b` (the null path throws; surviving = null path untested) |
| `empty-return` | returned value → `0` / `""` / `emptyList()` / `emptySet()` / `emptyMap()` |
| `range-boundary` | `until ↔ ..` on `Int`/`Long` ranges |
| `logical-operator` | `&& ↔ \|\|` |

## Configuration

```kotlin
mutation {
    targets.addAll("jvm", "iosSimulatorArm64") // default: all jvm targets
    timeoutSeconds.set(120)        // hard override; unset = derived below
    timeoutFactor.set(15.0)        // default: 15x the measured baseline
    minTimeoutSeconds.set(10)      // default: 10s floor for fast suites
    stallSeconds.set(30)           // default: 30, no finished test for this
                                   // long means hung; 0 disables
    baselineTimeoutSeconds.set(3600) // default: 1800 (30 min) for the whole suite
    coverageFiltering.set(false)   // default: true, see below
    parallelism.set(2)             // default: 1.5x the CPU cores
    batchSize.set(25)              // mutants verified per fork; 1 disables
    failOnSurvived.set(true)       // default: false (report only)
    scoreThreshold.set(80.0)       // default: 0.0 (never fails on score)
    includes.add("*billing*")      // default: whole module
    excludes.add("*Generated*")
    operators.addAll("math", "negate-conditional")  // default: all
    targetedTests.set(true)        // default: false, name-matched tests first,
                                   // survivors re-verified against the full suite
    maxMutationsPerFunction.set(200) // default: 100, skips are always reported
    // additionalJvmTestTasks.put("android-debug", "testDebugUnitTest")
    // instrumentAndroid.set(true) // experimental
}
```

Exclude a declaration (and everything nested in it) with
`@dev.pott.kaputt.runtime.DisableMutation`.

Generated code is never mutated. Everything under a module's build directory
is excluded automatically, including KSP and Room `_Impl` classes, BuildKonfig
and Compose resource accessors. No test should be expected to pin generated
code down, and mutating generated database plumbing corrupts native drivers
instead of producing a verdict. In one Room module that was 7535 of 9320
mutants.

An Android-only project needs no configuration: its `debug` compilation is
instrumented and `testDebugUnitTest` verifies the mutants. With flavours or
extra build types, name the variant with `androidVariant.set("freeDebug")`. A run
covers what that variant compiles, the same scope as its unit-test task.

In a multiplatform module the Android target is opt-in, since the jvm target
already covers common code: `instrumentAndroid` instruments it and
`additionalJvmTestTasks` names the unit-test task that verifies those mutants.

Instrumented tests verify mutants on a device with `instrumentedTests.set(true)`.
The apks carry every mutant, so they are installed once and each run only names
the one to activate; a mutant costs about as much as an `am instrument` call.
Coverage filtering does not apply there, so every mutant runs the whole suite.

## Verdicts

A mutant is killed when any selected target's tests fail on it. Timeouts count
as detected, since a mutant that never finishes is usually an infinite loop.
Survived means every target that contained the mutant ran green. Mutants the
baseline never executed are skipped rather than run, because no test can reach
them; turn that off with `coverageFiltering` when an exact run matters more
than speed.

## Incremental runs, baselines and CI

```bash
# Only mutate files changed since a git ref (plus uncommitted changes)
./gradlew :my:module:mutationTest -Pmutation.since=main

# Accept the current survivors as equivalent mutants (writes mutation-baseline.txt,
# check it in); later runs report them as IGNORED and keep them out of the score
./gradlew :my:module:mutationTest -Pmutation.updateBaseline=true

# Fast mode: run name-matched test classes first, re-verify survivors fully
./gradlew :my:module:mutationTest -Pmutation.targetedTests=true
```

`mutation.since` + `failOnSurvived`/`scoreThreshold` make a realistic PR gate:
only mutants in touched files run, and accepted equivalent mutants stay
baselined instead of blocking the build.

## Coverage filtering

The baseline records which tests execute each mutation point, and every mutant
is then verified by exactly those tests. A mutant no test reaches is reported
as survived without being run.

This assumes the suite covers the same code every run. A path that only
executes on some timings can be missed, and its mutants are reported as
survived rather than killed; the filter never turns a survivor into a kill.
Measured on a 1145-mutant module, filtering skips 215 of them and
reports 61.8% against 62.8% for an exact run, a smaller gap than the
run-to-run variance of that module's own timing-sensitive tests. Set
`coverageFiltering.set(false)` or `-Pmutation.coverageFiltering=false` for an
exact, slower run.

## Hangs

A mutant that breaks a loop guard never finishes. Forks are killed on lack of
progress rather than elapsed time: a fork that finishes no test for
`stallSeconds` is scored as detected, with `timeoutFactor` left as a backstop.
Duration alone predicts poorly, because a legitimate mutant can run far under
the baseline while machine load stretches another well past it.

Raise `stallSeconds` for suites that are slow to start, such as those waiting
on database containers, since anything producing no finished test for longer
than the window counts as hung.

## Performance

Measured on a 1145-mutant module on a 10-core machine, each step
building on the one before:

| Configuration | Wall time |
| --- | --- |
| Whole suite per mutant | 497s |
| Only the test classes coverage attributed | 255s |
| Only the test methods coverage attributed | 224s |
| 25 mutants per fork | 172s |
| 1.5x cores in parallel | 120s |

The score stays 59.7% at every step, so none of it trades accuracy for speed.
What remains is mostly per-test-class setup inside the fork plus a fixed ~18s
instrumented compile.

On macOS the forks run as accessory processes so tests touching AWT or Skiko
do not steal focus or switch Spaces.

## Known limitations

- Java sources are never mutated, only Kotlin.
- `inline` functions are skipped. The inliner regenerates their bodies at every
  call site, which miscompiles instrumented initializers.
- `@Composable` functions are skipped, since unit tests cannot meaningfully
  kill UI mutants.
- `const val` and delegated-property initializers, compound assignments,
  `++`/`--`, and the structural constants of desugared `?:`, `?.`, `&&` and
  `||` are never mutated. The Kotlin backends rewrite those shapes and assert
  on them.
- Some survivors are equivalent mutants, such as a removed log call. Silence
  them with `excludes` or `@DisableMutation` rather than writing a contrived
  test.
- Parallel runs assume isolated tests. Set `parallelism.set(1)` for suites
  sharing ports, files or other global state.

## Compatibility

Each release tracks one Kotlin minor line, currently 2.4.x: the compiler plugin
links against `kotlin-compiler-embeddable` internals, and the Gradle plugin
warns when the host build's Kotlin does not match. Developed against Gradle 9.x
with the configuration cache enabled.

Verified on macOS against jvm, `iosSimulatorArm64`, `macosArm64`, an
`androidJvm` host-test task and an instrumented run on an emulator. JS and wasm are not supported. Windows is
untested.

## AI assistance

Parts of this code base were written with AI tools and reviewed by me. Every
operator has a test that runs the actual mutant, and CI checks the example
projects against their expected results, so bugs have to get past the tests,
not just past me. If one does, please open an issue.

## License

[Apache License 2.0](LICENSE)
