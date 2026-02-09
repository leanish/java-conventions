# AGENTS.md

Scope: this repository (`leanish/gradle-conventions`) only.

This file defines the minimum bar for any change made by agents or humans.

## Purpose

This repository publishes `io.github.leanish.gradle-conventions`.
It is a shared build contract. Treat behavior changes as user-facing API changes.

## Public Contract (must stay documented)

Any change that affects plugin consumers must be treated as a contract change.
This includes:

- Plugin id and version usage (`io.github.leanish.gradle-conventions`).
- Applied plugins and task wiring.
- Defaults for toolchains, repositories, quality tools, and publishing.
- Added dependencies and their scopes.
- Convention properties and defaults under `leanish.conventions.*`.
- Task behavior that impacts developer workflow (`installGitHooks`, `setupProject`, `build` and `check` dependencies).
- Tag-based test filtering and coverage behavior (`-DexcludeTags=...`).

If contract behavior changes, update tests and README in the same change.

## Compatibility Policy

Current baseline:

- Gradle baseline: wrapper version from `gradle/wrapper/gradle-wrapper.properties` (currently `9.3.1`).
- CI baseline: JDK `25` (`.github/workflows/ci.yml`).
- Plugin build toolchains: Java `25` and Kotlin/JVM toolchain `21`.
- Consumer default conventions: Java toolchain/release `25` unless overridden by the consumer build.

Rules:

- Do not change Gradle/JDK baselines silently.
- If a baseline changes, update tests, CI, and README in the same PR.
- Keep compatibility statements concrete and versioned.

## Required README Update Checklist

When behavior changes, verify and update `README.md` before merging.

- `What it provides` reflects the real defaults and toggles.
- All versioned snippets use the new plugin version.
- `Convention properties` includes every supported property and default.
- Publishing section matches actual repositories, credentials, and defaults.
- Coverage/test behavior reflects real `excludeTags` and verification rules.
- Spotless, Checkstyle, and Error Prone sections match real configuration.
- Notes capture important side effects and opt-out paths.

If no README change is needed, explicitly confirm why in the PR description.

## Test Expectations

Any behavior change requires tests in `src/test/kotlin/io/github/leanish/gradleconventions/GradleConventionsPluginTest.kt`.

- Add or update tests for default behavior.
- Add or update tests for override/opt-out behavior.
- For regressions, add a failing-case test first, then fix behavior.
- Validate externally visible outcomes (task presence/wiring, generated POM/config, applied conventions).
- Keep tests deterministic and avoid assertions on incidental ordering.

Before finishing work, run:

- `./gradlew check`

## Release Checklist

For each release:

- Bump version in `build.gradle.kts`.
- Update all README version references and examples.
- Run `./gradlew check`.
- Run `./gradlew publishToMavenLocal` and smoke-test consumption by plugin id + version.
- Publish with `./gradlew publishPlugins` (with required credentials configured).
- Create/push git tag for the release version (`vX.Y.Z`).
- Ensure the published behavior matches README and tests at release commit.

## Change Style

- Keep changes small and focused.
- Preserve backward compatibility unless an intentional breaking change is approved.
- Prefer explicit defaults and explicit failure messages over implicit behavior.
