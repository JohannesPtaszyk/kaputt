# Why this exists

Line coverage answers "did this line run". It cannot answer "would a test have
noticed if the line were wrong". Those are different questions, and only the
second one is about the tests.

A covered line with no assertion behind it looks identical to a well tested one
in a coverage report. Mutation testing closes that gap by changing the code and
checking whether the suite complains.

## What a run finds

From a real codebase, all of these were fully covered lines in a green suite:

```
Entitlement.isActive:29        replaced '<' with '<='
LoadRampGuardrail.check:15     replaced '<=' with '<'
AdherenceResolver.summarize:35 replaced '<' with '<='
```

Each is an untested boundary. The first decides whether a paid subscription is
still active at the exact expiry instant. Nothing in the suite pinned it down,
and coverage reported the line as tested.

## What it does not find

Mutants only reveal what your assertions ignore. They say nothing about missing
features, wrong requirements, or behavior nobody wrote a test for at all.

A score is also a poor target on its own. Data classes, preset tables and
generated code produce mutants no reasonable test should kill, which is why the
report leads with the survivors worth acting on rather than the percentage.

## When it is worth running

Auditing a module you are about to trust: billing, scoring, sync, anything
where a boundary is money or safety. Run it once, fix the survivors that
matter, move on.

On a branch: `-Pmutation.since=main` limits the run to files you touched, which
is seconds to minutes rather than an hour.

Chasing a repository-wide percentage is usually not worth it.
