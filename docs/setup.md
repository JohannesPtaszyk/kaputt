# Setup

## Requirements

- Kotlin 2.4.x. The compiler plugin links against `kotlin-compiler-embeddable`
  internals, so each release tracks one Kotlin minor line and warns when the
  host build does not match.
- Gradle 9.x, configuration cache supported.
- JDK 11 or newer for the test JVM.

## Adding the plugin

Until artifacts are published, consume it as a composite build.

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("path/to/kotlin-mutation-testing")
}
includeBuild("path/to/kotlin-mutation-testing")
```

Apply it in the root build to cover every module, or in a single module.

```kotlin
// build.gradle.kts
plugins {
    id("dev.pott.kaputt")
}
```

Applying it in the root registers `mutationTest` in every subproject that has a
Kotlin jvm, native or Android target. Nothing runs until you invoke the task.

## Running

```bash
./gradlew :my:module:mutationTest                  # one module
./gradlew mutationTest --continue                  # every module
./gradlew :my:module:mutationTest -Pmutation.since=main   # changed files only
```

Reports land in `build/reports/mutation-test/`. Open `mutation-report.html`.

## Test frameworks

JUnit 4 and the JUnit Platform both work. The fork picks whichever the test
classpath carries, so `kotlin-test` on its jvm default, Jupiter on JUnit 5
or 6, and platform engines such as Kotest all run. Both platform lines are
covered by their own example, since JUnit 5 ships launcher 1.x and JUnit 6
ships launcher 6.x. A Platform suite needs
`junit-platform-launcher` on the test runtime classpath, which Gradle adds for
you when the build calls `useJUnitPlatform()`.

Robolectric works but needs `coverageFiltering.set(false)`, since coverage
cannot see inside its classloader.

Java sources are never mutated. A mixed module has its Kotlin covered and its
Java left alone, whatever generates it.

## Project types

- **Kotlin JVM**: works as is, verified by the `test` task.
- **Kotlin Multiplatform**: jvm targets by default. Native targets are opt-in
  with `-Pmutation.targets=jvm,iosSimulatorArm64` because each mutant reruns
  their test binaries.
- **Android**: an application or a library is instrumented on its `debug`
  compilation and verified by `testDebugUnitTest`, with no extra
  configuration. A project with flavours or extra build types names the
  variant that carries the mutants:

  ```kotlin
  mutation { androidVariant.set("freeStaging") }
  ```

  Picking a variant that has no unit-test task fails with the list of the ones
  that do. A module whose unit tests use Robolectric needs
  `coverageFiltering.set(false)`, because coverage cannot see inside its
  classloader; the run warns when it finds one. A multiplatform module with an
  Android target opts in with `instrumentAndroid` and `additionalJvmTestTasks`
  instead.
- **Android instrumented tests**: `instrumentedTests.set(true)` verifies the
  mutants with the `androidTest` suite on a connected device instead of with
  the unit tests.

  ```kotlin
  mutation {
      instrumentedTests.set(true)
      // deviceSerial.set("emulator-5554") // when more than one is attached
  }
  ```

  Both apks carry every mutant, so they are built and installed once and each
  run only names the mutant to activate. Coverage filtering does not apply, so
  every mutant runs the whole instrumented suite: keep the module small or
  narrow the operators. The loop deadline cannot reach the app process either,
  so a mutant that breaks a loop guard on a device runs until the per-mutant
  timeout.

## Cost

Instrumentation only happens in builds that invoke `mutationTest`. Every other
build compiles untouched code, and applying the plugin costs nothing
measurable at configuration time.
