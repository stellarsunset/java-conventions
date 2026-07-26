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
| [SpotBugs](https://spotbugs.github.io) | `com.github.spotbugs` | Bytecode static analysis. |
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

## Building & publishing

This project mirrors the [auto-semver](https://github.com/stellarsunset/auto-semver) publishing setup:
the [Vanniktech Maven Publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
publishes to Maven Central (and, via `GradlePublishPlugin`, the Gradle Plugin Portal) with signed
artifacts.

```bash
just test            # ./gradlew test functionalTest
just release minor   # tag, push, and publish a new version
```
