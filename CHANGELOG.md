# Changelog

## Unreleased

First public version, as kaputt (German: broken).

- Mutant schemata generated in Kotlin IR: every mutation is compiled once
  behind a guard, so a module compiles once and each mutant is a test run
  rather than a rebuild.
- 12 operators covering conditionals, arithmetic, ranges, negation, safe calls,
  returns and literals.
- Kotlin Multiplatform: jvm, native and Apple simulator targets from the same
  manifest, with mutant ids that match across targets.
- Android: unit tests by default, or instrumented tests on a device with
  `instrumentedTests.set(true)`. Flavours and build types via `androidVariant`.
- Coverage-filtered verification: the baseline records which tests reach each
  mutation point, and a mutant is verified by exactly those tests.
- Only the targets a run verifies are instrumented, so a project may declare
  platforms the runtime is not published for.
- Boolean returns are forced to both `false` and `true`, so a predicate tested
  only on the cases that hold is no longer reported as pinned down. Forced
  returns and dropped calls now rank as untested behavior rather than as
  incidental survivors.
- A deadline compiled into every loop body ends runaway mutants from the
  inside rather than hanging until the test process is killed.
- Reports as HTML with diffs, PIT XML, SARIF, the Stryker schema, markdown and
  json, plus a baseline file for accepted survivors.
