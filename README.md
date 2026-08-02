# java-conventions

[![Test](https://github.com/stellarsunset/java-conventions/actions/workflows/test.yaml/badge.svg)](https://github.com/stellarsunset/java-conventions/actions/workflows/test.yaml)
![Claude](https://img.shields.io/badge/Claude_Code-555?logo=claude)

An opinionated Gradle conventions plugin that bundles the linting, formatting, static-analysis,
coverage, and versioning tools used across my open-source Java repositories into a single, applyable
plugin. It follows [Google Java conventions](https://google.github.io/styleguide/javaguide.html)
wherever a Google-blessed configuration exists.

## Motivation

Every repo ends up copy-pasting the same block of ErrorProne, Spotless, and Checkstyle wiring, and
then drifting out of sync. Packaging that wiring as one versioned plugin means:

1. Single import for all Java repos
2. Consistent, Google-aligned rules everywhere, updated in one place.

## What's included

Applying the plugin applies and configures all of the following:

| Tool | Plugin id | Purpose |
| --- | --- | --- |
| Java + JaCoCo | `java`, `jacoco` | Base compilation and code-coverage reporting (XML + HTML). |
| [ErrorProne](https://errorprone.info) | `net.ltgt.errorprone` | Compile-time bug detection. |
| [NullAway](https://github.com/uber/NullAway) | `net.ltgt.nullaway` | Null-safety, enforced only for packages you opt in. |
| [Spotless](https://github.com/diffplug/spotless) | `com.diffplug.spotless` | Formatting via [google-java-format](https://github.com/google/google-java-format). |
| Checkstyle | `checkstyle` | Google's `google_checks.xml`, pulled from the Checkstyle jar (with `MissingJavadocType`/`MissingJavadocMethod` suppressed, so Javadoc isn't required on every public API). |
| [SpotBugs](https://spotbugs.github.io) | `com.github.spotbugs` | Bytecode static analysis (with `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` excluded, so records/DTOs can expose mutable components without defensive copies). |
| [auto-semver](https://github.com/stellarsunset/auto-semver) | `io.github.stellarsunset.auto-semver` | Automatic semantic versioning from annotated git tags. |

By default, violations from Checkstyle, SpotBugs, ErrorProne, and Spotless formatting drift all fail
the `check` task.

## Usage

```kotlin
plugins {
    id("io.github.stellarsunset.java-conventions") version "0.1.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
```

The bundled auto-semver plugin sets `project.version` from your  latest annotated git tag and adds a `release` task — see
[its README](https://github.com/stellarsunset/auto-semver) for details.

## Configuration

The plugin is deliberately low-config. The `javaConventions` extension exposes the two knobs that
matter in practice:

```kotlin
javaConventions {
    // Packages NullAway should treat as @NonNull-by-default and enforce null-safety within.
    // NullAway stays off until at least one package is listed, so it is safe to adopt gradually.
    nullAwayAnnotatedPackages.add("io.github.stellarsunset")

    // Report Checkstyle/SpotBugs findings without failing the build. Defaults to false.
    // Useful when onboarding an existing codebase with pre-existing violations.
    ignoreFailures.set(false)
}
```

`ignoreFailures` governs Checkstyle and SpotBugs. ErrorProne is enforced by the compiler and Spotless
by `spotlessCheck`; run `./gradlew spotlessApply` to auto-format.

## Repo template

The plugin also carries the standard repo-hygiene files so a fleet of repos can share them and update
them in one place. Run:

```bash
./gradlew applyRepoTemplate            # write/refresh the files
./gradlew applyRepoTemplate --preview  # show what would change, write nothing
```

into the repository root (wherever the plugin is applied). Two flavours of file:

- **Managed** — owned by the plugin and rewritten every run; each carries a "do not hand-edit"
  header stamped with the plugin version. Change them in this plugin, not in the consuming repo.
  Covers `.gitignore`, `.gitattributes`, `.editorconfig`, `renovate.json5`, and the shared
  `.idea/` settings (`externalDependencies.xml`, `google-java-format.xml`, `codeStyles/`).
- **Seed** — written only when absent, then owned by the repo. Currently just `justfile`.

The `.idea` files make IntelliJ prompt to install the [google-java-format](https://github.com/google/google-java-format)
plugin and enable it, so **Reformat Code** matches `spotlessApply`. You still need to add the
`--add-exports` VM options that google-java-format requires (those are IDE-global, not per-repo).

## Building & publishing

Publishing is handled by the sibling [auto-publish](https://github.com/stellarsunset/auto-publish)
plugin, which derives the Maven Central publication (coordinates, POM, signing) from git plus the
`license` in `gradle.properties` — no hand-written `mavenPublishing`/`pom` block. `just release`
tags via auto-semver and pushes the bundle to Maven Central.

```bash
just test            # ./gradlew test functionalTest
just release minor   # tag, push, and publish (./gradlew publishToMavenCentral) a new version
```
