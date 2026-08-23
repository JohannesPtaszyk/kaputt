---
name: Bug report
about: Something broke, miscompiled, or reported a wrong verdict
labels: bug
---

**What happened**

**Minimal reproduction**
The Kotlin snippet that triggers it, or a link to a small project. Compiler
crashes usually reduce to a single function.

**Versions**
- Plugin:
- Kotlin:
- Gradle:
- Targets involved (jvm / native / Android unit / Android instrumented):

**Logs**
For wrong verdicts, attach the mutant's log from
`build/reports/mutation-test/logs/`. For compiler crashes, the stack trace
down to the first `org.jetbrains.kotlin` frame.
