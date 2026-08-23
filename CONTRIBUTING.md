# Contributing

## Building and testing

The project is a standalone Gradle build (it also works embedded as a
composite build):

```bash
./gradlew build            # compiles everything and runs all test suites
./gradlew :kaputt-compiler-plugin:test   # IR operator tests (compile real fixtures, run mutants)
./gradlew :kaputt-gradle-plugin:test     # report renderers, classification, command building
./gradlew :kaputt-runtime:jvmTest :kaputt-runtime:linuxX64Test
```

The example projects under `examples/` are run by `ExampleProjectTest`, which
holds them to the results they report. The Android ones need an SDK and skip
without one.

The compiler-plugin tests invoke the real K2 compiler with the freshly built
plugin jar, load the fixtures in isolated classloaders and assert that each
mutant flips behavior at runtime. Add a fixture and a behavioral test for every
new operator.

## Style

`./gradlew spotlessApply` formats everything (ktlint); CI runs `spotlessCheck`.

## Ground rules

- Work test-first. Every operator needs a behavioral test that runs the mutant,
  not only an assertion about the manifest.
- The compiler backends assert IR shapes without saying so. If you touch
  instrumentation, validate against a real multi-module project, not only the
  fixtures.
- Keep the runtime dependency-free and multiplatform, and the Gradle plugin
  configuration-cache compatible.
- `coverageFiltering` is on by default and decides which mutants run at all,
  so a bug there changes every score. Only its selection predicate is unit
  tested; after touching coverage collection or selection, A/B one module and
  expect identical verdicts:

  ```bash
  ./gradlew :some:module:mutationTest
  cp some/module/build/reports/mutation-test/mutation-report.json /tmp/on.json
  ./gradlew :some:module:mutationTest -Pmutation.coverageFiltering=false
  # diff the per-mutant "status" fields against /tmp/on.json
  ```

  A 130-mutant module scored 56.2% both ways, the only difference a
  KILLED/TIMED_OUT flake, and both of those count as detected. Filtering may
  only ever skip a mutant into SURVIVED. It must never turn one into KILLED.
- Unit tests cannot cover the per-target execution wiring. After touching
  target selection, task dependencies, or the manifest contract, run a real
  multi-target job against a consuming project, e.g.
  `./gradlew :some:module:mutationTest -Pmutation.targets=jvm,iosSimulatorArm64`,
  and confirm `build/reports/mutation-test/logs/` holds a log per target. A
  target that produced only `baseline-<target>.log` verified nothing. A missing
  manifest fails the task; keep it that way.

## Licence

Contributions are made under the Apache 2.0 licence that covers this project.
