# Configuration

Everything is optional. The defaults run every operator against the jvm
targets, report without failing the build, and filter by coverage.

```kotlin
mutation {
    targets.addAll("jvm", "iosSimulatorArm64")
    operators.addAll("conditional-boundary", "negate-conditional")
    includes.add("*billing*")
    excludes.add("*Generated*")

    failOnSurvived.set(false)
    scoreThreshold.set(60.0)

    coverageFiltering.set(true)
    batchSize.set(25)
    parallelism.set(8)

    timeoutFactor.set(15.0)
    timeoutSeconds.set(120) // fixed per-mutant timeout; unset derives from the baseline
    stallSeconds.set(30)
    baselineTimeoutSeconds.set(1800)
    maxMutationsPerFunction.set(100)

    instrumentAndroid.set(false)
    androidVariant.set("debug")
    instrumentedTests.set(false)
    additionalJvmTestTasks.put("android", "testDebugUnitTest")
}
```

## Selecting code

`includes` and `excludes` are globs matched against the source path and against
`package.FileName.kt`. Generated code under the build directory is always
excluded.

Annotate a declaration to exclude it and everything nested inside:

```kotlin
@dev.pott.kaputt.runtime.DisableMutation
fun equivalentByDesign() = Unit
```

## Android variants

An Android-only project mutates one variant, `debug` unless you say otherwise.
With flavours or extra build types there is no plain `debug`, so name the
variant that carries the mutants, such as `freeDebug` or `paidStaging`. A run mutates what that variant compiles, `src/main` plus the variant's own
source sets, matching what `assembleFreeDebug` and `testFreeDebugUnitTest`
cover. Flavour-specific code in another flavour belongs to another run: point
`androidVariant` at it and run the task again.

```kotlin
mutation { androidVariant.set("freeDebug") }
```

`instrumentedTests` moves the verdict to the `androidTest` suite of that
variant, running on a connected device. `deviceSerial` names the device when
more than one is attached; without it a run refuses to guess.

## Selecting targets

Empty means every jvm target. Native targets are opt-in because each mutant
reruns their test binaries, and targets whose tests cannot run on the current
host are skipped with a warning.

## Failing a build

`failOnSurvived` fails on any survivor, which is realistic only with
`-Pmutation.since` on a branch. `scoreThreshold` fails below a percentage; set
it a few points under what a module scores today so it ratchets rather than
blocking.

## Accepting equivalent mutants

Some survivors cannot be killed by any reasonable test. Record them once:

```bash
./gradlew :my:module:mutationTest -Pmutation.updateBaseline=true
```

That writes `mutation-baseline.txt` next to the build file. Check it in. Later
runs report those mutants as ignored and leave them out of the score. The ids
are derived from source offsets, so editing the file invalidates its entries,
which is intentional.

## Properties

Every run can be steered without editing the build file:

| Property | Effect |
| --- | --- |
| `-Pmutation.since=<ref>` | only mutants in files changed since the ref |
| `-Pmutation.target=<glob>` | narrow to matching files |
| `-Pmutation.targets=a,b` | pick the targets that verify mutants |
| `-Pmutation.coverageFiltering=false` | run every mutant, including unreached ones |
| `-Pmutation.targetedTests=true` | name-matched tests first, survivors reverified |
| `-Pmutation.updateBaseline=true` | rewrite the accepted-mutant baseline |
| `-Pmutation.enabled=true` | instrument without running mutants |

## Command-line overrides

`-Pmutation.batchSize` and `-Pmutation.parallelism` override their extension
counterparts for one run, next to `-Pmutation.targets`, `-Pmutation.since`,
`-Pmutation.targetedTests` and `-Pmutation.coverageFiltering`. Useful when a
CI machine has different cores than the laptop the defaults were tuned on.
