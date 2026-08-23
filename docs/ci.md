# Continuous integration

A full sweep is a nightly job. Scoping by diff is what fits a pull request.

## On a branch

```bash
./gradlew mutationTest --continue -Pmutation.since=origin/main
```

Only mutants in changed files run, which is usually seconds to minutes. Combine
with `failOnSurvived` to block new untested logic while leaving the existing
backlog alone.

## Nightly

```yaml
on:
  schedule:
    - cron: "0 2 * * 1"

jobs:
  instrument:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      # Instrumenting is cheap and catches compiler failures that only a new
      # code shape triggers, without running a mutant.
      - run: ./gradlew compileKotlinJvm -Pmutation.enabled=true --continue

  measure:
    needs: instrument
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew mutationTest --continue
      - name: Summary
        if: always()
        run: |
          for f in $(find . -name mutation-report.md -path '*mutation-test*'); do
            cat "$f" >> "$GITHUB_STEP_SUMMARY"
          done
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: mutation-reports
          path: "**/build/reports/mutation-test/**"
```

Modules are independent, so a matrix over groups of them turns a long sweep
into several short ones.

## Code scanning

`mutation-report.sarif` lists survivors as warnings anchored to their exact
span, which GitHub renders inline on the pull request.

```yaml
      - uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: my/module/build/reports/mutation-test/mutation-report.sarif
```

## Guarding the filter

Coverage filtering decides which mutants run at all, so a job that runs one
module both ways is worth the minutes. Filtering may only ever skip a mutant
into survived; a mutant it reports as killed while an exact run does not means
attribution is wrong.
