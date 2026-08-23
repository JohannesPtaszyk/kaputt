# Examples

Six runnable projects live under `examples/`. Each consumes the plugin from
this repository as a composite build, so nothing needs publishing first.

```bash
cd examples/jvm-junit4-app && ./gradlew mutationTest
```

Every example ships a deliberate gap: the tests pass, coverage looks fine, and
a boundary goes unverified.

## jvm-junit4-app

A Kotlin JVM project. `Basket.total` applies a discount above a threshold, and
the tests check a small order and a large one without ever touching the
threshold itself.

```
2 untested behaviors, 0 other survivors
10 mutants: 8 killed, 2 survived
  - Basket.kt:13: replaced '>' with '>='
  - Basket.kt:20: replaced '>=' with '>'
```

## jvm-junit5-app and jvm-junit6-app

The same basket as `jvm-junit4-app`, tested with Jupiter. Neither configures anything
extra: the fork uses whichever framework the test classpath carries, and both
report the same ten mutants and the same two survivors as the JUnit 4 version.

They exist separately because JUnit 5 ships platform 1.x and JUnit 6 ships
platform 6.x, and the driver binds to the platform rather than to Jupiter.

## kmp-library

A multiplatform library with jvm, macOS and Linux targets. `Retry.shouldRetry`
stops at an attempt limit that no test pins down.

```bash
./gradlew mutationTest                                  # jvm only, the default
./gradlew mutationTest -Pmutation.targets=jvm,macosArm64
```

Both targets report the same mutant ids, because the mutants come from the
shared IR rather than from each platform's bytecode.

## android-app

An Android application. `BatterySaver.maySync` refuses to sync on a low
battery, and the tests check a healthy battery and a flat one but not the
boundary. It needs no mutation configuration: an Android-only project is
instrumented on its debug compilation and verified by `testDebugUnitTest`.

Set `sdk.dir` in `local.properties` or `ANDROID_HOME` before running it.

## android-instrumented

The same shape as `android-app`, but the tests live in `src/androidTest` and
run on a device. `SyncWindow.maySync` holds queued work back on a metered
network or a low battery, and the tests miss the battery boundary.

```kotlin
mutation { instrumentedTests.set(true) }
```

`mutationTest` builds and installs both apks once, then runs the suite through
`am instrument` per mutant. It needs a booted emulator or an attached device.

## android-flavors

An Android app with `free` and `paid` flavours, a `staging` build type beyond
debug and release, and shared sync logic in `src/main`. Flavours and build
types multiply into variants, none of them called plain `debug`, so the build
names the one that carries the mutants:

```kotlin
mutation { androidVariant.set("paidStaging") }
```

A run covers `src/main` and the chosen flavour, the same scope as
`testPaidDebugUnitTest`. Switching the variant changes what is measured, and
both answers are true:

| Variant | Mutants | Survivors |
| --- | --- | --- |
| `paidStaging` | 7 | the grace allowance no test pins down |
| `freeDebug` | 6 | the free limit, whose only test lives in `src/testPaid` |

The second is worth sitting with. `DeviceLimitTest` is compiled into the paid
variants only, so the free tier's limit has no test at all, and a run of the
free variant is what says so.
