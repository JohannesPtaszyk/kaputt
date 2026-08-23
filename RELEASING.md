# Releasing

The version lives in one place: `gradle.properties` (`version=`). The Gradle
plugin reads it from a generated jar resource at runtime to resolve the
matching `compiler-plugin` and `runtime` artifacts, so a release is consistent
by construction.

## Before a release

- [x] Instrument a few large third-party Kotlin codebases
      (`-Pmutation.enabled=true` with a compile task, then a baseline run).
      Every backend-shape issue so far surfaced on a code pattern the fixtures
      did not have, so a corpus run is the cheapest way to find the next one.
      Arrow 2.x, on the same Kotlin line: 1580 mutants across 12 modules, no
      compiler failure and no skipped function, and `arrow-autoclose` killed
      all 8 of its mutants. Most Kotlin libraries lag a minor version or two
      behind, which is what limits the corpus.
- [x] Run `mutationTest -Pmutation.targets=jvm,iosSimulatorArm64` on a macOS
      host: mutants execute on the simulator, an iOS-only run killed 13 of 13,
      and manifest ids match jvm.
- [x] Run the instrumented example on a device: `am instrument` per mutant,
      four mutants, one survivor, about half a second each.
- [x] Consume the published artifacts rather than the composite build, which
      is the only way to catch broken coordinates or a stale version resource:

      ./gradlew publishToMavenLocal

      then a project with `mavenLocal()` in `pluginManagement` and
      `dependencyResolutionManagement`, `id("dev.pott.kaputt") version "..."`
      and nothing else, must report survivors on `./gradlew mutationTest`.
- [ ] Decide and document the supported Gradle range. Developed against
      Gradle 9.x with the configuration cache on; Windows is untested.

## Distribution

`compiler-plugin` and `runtime` are ordinary dependencies the consumer's build
resolves, so they have to be on Maven Central whichever way the plugin itself
is distributed. The Plugin Portal on top of that only saves consumers the
`mavenCentral()` line in `pluginManagement`.

Neither is configured yet. Both need:

1. A Central Portal namespace. `dev.pott` needs the matching domain verified;
   `io.github.<user>` needs no domain and is the fallback.
2. A signing key (`signing.keyId`/`signing.password`/`signing.secretKeyRingFile`
   or the in-memory variant for CI) and portal credentials, kept out of the
   repository.
3. Publishing to the portal, which plain `maven-publish` cannot do on its own.

## Kotlin versions

The compiler plugin links against `kotlin-compiler-embeddable` internals, so a
release supports one Kotlin minor line, currently 2.4.x. Name that line in the
release notes; the Gradle plugin warns when the host build disagrees.

## Local / composite consumption

No release needed. Composite builds substitute the modules directly, and
`./gradlew publishToMavenLocal` publishes all three artifacts plus the plugin
marker for testing against other local projects.
