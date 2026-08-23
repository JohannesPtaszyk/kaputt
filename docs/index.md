# kaputt

kaputt (German for *broken*) is mutation testing for Kotlin: it changes small
pieces of your code and reruns the tests. A test
that fails kills the mutant. A mutant that survives marks logic no test checks.

This plugin generates mutants in Kotlin IR, so one pass covers the jvm and
native targets of a multiplatform module, and each target verifies them with
its own kotlin-test runner.

- [Why this exists](why.md)
- [Setup](setup.md)
- [How it works](how-it-works.md)
- [Operators](operators.md)
- [Configuration](configuration.md)
- [Reading a report](reports.md)
- [Continuous integration](ci.md)
- [Examples](examples.md)

## In one minute

```kotlin
// settings.gradle.kts
pluginManagement { includeBuild("path/to/kotlin-mutation-testing") }
includeBuild("path/to/kotlin-mutation-testing")

// build.gradle.kts
plugins { id("dev.pott.kaputt") }
```

```bash
./gradlew :my:module:mutationTest
```

```
2 untested behaviors, 0 other survivors
10 mutants: 8 killed, 0 timed out, 2 survived, 0 errors
Mutation score: 80.0%
Untested behavior, most actionable first:
  - Basket.kt:13: replaced '>' with '>='
  - Basket.kt:20: replaced '>=' with '>'
```

Those two lines are boundaries the suite never pins down. The code is covered;
the behavior is not.
