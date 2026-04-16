# Changelog

## 0.5.4

### Changed
- Prepared the `0.5.4` release after `0.5.3`.
- Upgraded the Gradle wrapper from `9.4.0` to `9.4.1`.
- Upgraded plugin build dependencies:
  - `com.gradle.plugin-publish` from `2.1.0` to `2.1.1`
  - `com.diffplug.spotless` from `8.3.0` to `8.4.0`
- Upgraded GitHub Actions Gradle setup from `gradle/actions/setup-gradle@v5` to `gradle/actions/setup-gradle@v6`.
- Upgraded Checkstyle from `13.3.0` to `13.4.0`.
- Upgraded consumer-injected dependencies:
  - `org.projectlombok:lombok` from `1.18.42` to `1.18.44`
  - `com.google.errorprone:error_prone_annotations` from `2.48.0` to `2.49.0`
  - `com.google.errorprone:error_prone_core` from `2.48.0` to `2.49.0`
  - `com.uber.nullaway:nullaway` from `0.13.1` to `0.13.3`

## 0.5.3

### Changed
- Upgraded the Gradle wrapper from `9.3.1` to `9.4.0`.

### Fixed
- Fixed plugin validation failure on Gradle `9.4.0` by making the generated Checkstyle config task explicitly cacheable.
- Added a regression test that applies the plugin to a consumer project and runs `checkstyleMain` on the current Gradle baseline.

## 0.5.2

### Added
- Added `org.projectlombok:lombok:1.18.42` to consumer `testCompileOnly` dependencies,
  aligning test compile classpath behavior with existing Lombok test annotation processing.

### Changed
- Upgraded Spotless from `8.2.1` to `8.3.0`.

## 0.5.1

### Changed
- Renamed plugin-project runtime override property from `javaConventions.testRuntimeJdkVersion`
  to `javaConventions.runtimeJdkVersion` in build logic, legacy JDK workflow, and maintainer docs.

## 0.5.0

### Changed
- Lowered plugin JAR bytecode target from JVM 21 to JVM 17;
  the plugin can now be loaded by Gradle running on JDK 17+.
- Added fail-fast validation that rejects consumer Java toolchains below JDK 21 with a clear error message.
- Upgraded Checkstyle from `12.1.2` to `13.3.0`. Consumers with a custom
  `config/checkstyle/checkstyle.xml` should verify compatibility.
- Upgraded plugin build dependency:
  - `net.ltgt.gradle:gradle-errorprone-plugin` from `5.0.0` to `5.1.0`
- Upgraded consumer-injected annotation dependencies:
  - `org.jetbrains:annotations` from `26.0.2-1` to `26.1.0`
  - `com.google.errorprone:error_prone_annotations` from `2.47.0` to `2.48.0`
  - `com.google.errorprone:error_prone_core` from `2.47.0` to `2.48.0`

## 0.4.0

### Added
- Optional `mavenLocal()` dependency repository toggle (disabled by default):
  - `leanish.conventions.repositories.mavenLocal.enabled`
  - `JAVA_CONVENTIONS_MAVEN_LOCAL_ENABLED`
- Fine-grained publishing toggle for GitHub Packages:
  - `leanish.conventions.publishing.githubPackages.enabled`
  - `JAVA_CONVENTIONS_PUBLISHING_GITHUB_PACKAGES_ENABLED`
- Exposed `io.github.leanish.gradleconventions.ConventionProperties` for wrapper plugins so shared property/env names can be imported instead of duplicated.
- Explicit fail-fast validation when a repository named `GitHubPackages` already exists but is not a Maven repository.

### Changed
- Publishing conventions no longer auto-add `mavenLocal()` as a publishing repository.
- GitHub Packages publishing is now independently toggled from overall publishing conventions.

## 0.3.1

### Changed
- Upgraded consumer-injected test dependencies:
  - `org.junit.jupiter:junit-jupiter` from `6.0.2` to `6.0.3`
  - `org.junit.platform:junit-platform-launcher` from `6.0.2` to `6.0.3`

## 0.3.0

### Breaking changes
- Renamed plugin id from `io.github.leanish.gradle-conventions` to `io.github.leanish.java-conventions`.
- Renamed project/repository from `gradle-conventions` to `java-conventions`.
- Removed compatibility alias; consumers must use the new plugin id.

### Added
- Publishing conventions:
  - automatic `maven-publish` setup (toggleable via `leanish.conventions.publishing.enabled` /
    `JAVA_CONVENTIONS_PUBLISHING_ENABLED`)
  - `mavenJava` publication defaults
  - POM license metadata (MIT)
- Generic publishing metadata resolution:
  - owner resolution via `GITHUB_REPOSITORY_OWNER`, `JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER`,
    `leanish.conventions.publishing.githubOwner`, or `group` (`io.github.<owner>`)
  - developer id/name/url can be configured independently and are inferred from owner when missing
- Configurable repositories:
  - `mavenCentral()` can now be disabled via
    `leanish.conventions.repositories.mavenCentral.enabled` /
    `JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED`
- NullAway base package behavior:
  - `leanish.conventions.basePackage` / `JAVA_CONVENTIONS_BASE_PACKAGE` is optional
  - when missing, base package(s) are inferred from `src/main/java` package declarations and logged
- Spotless license header support:
  - applies `LICENSE_HEADER` automatically when present
- Java artifact conventions:
  - enables `withSourcesJar()` and `withJavadocJar()`
- Test dependency conventions:
  - adds `org.junit.jupiter:junit-jupiter:6.0.2`
  - adds `org.assertj:assertj-core:3.27.7`
  - keeps JUnit Platform launcher (`org.junit.platform:junit-platform-launcher:6.0.2`) as `testRuntimeOnly`
- Plugin self-publishing support to GitHub Packages (`leanish/java-conventions`).

### Changed
- Upgraded Error Prone Gradle plugin to `5.0.0`.
- Upgraded Error Prone core/annotations to `2.47.0`.
- Refactored conventions logic into focused support classes:
  - `ConventionProperties`
  - `JavaConventionsProviders`
  - `PropertyParser`
  - `BasePackageDetector`
  - `GithubOwnerResolver`
- Replaced script-closure Checkstyle file generation with typed task:
  - `WriteCheckstyleConfigTask`

### Fixed
- Checkstyle conventions now respect consumer project files when provided:
  - `config/checkstyle/checkstyle.xml`
  - `config/checkstyle/suppressions.xml`
- Improved root git hook installation to support `.git` pointer/worktree setups.
- Expanded configuration-cache compatibility coverage and improved CI stability.
- Increased and enforced plugin test coverage with JaCoCo instruction checks.
