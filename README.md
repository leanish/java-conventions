# java-conventions

Shared Gradle conventions for JDK-based projects.

## What it provides
- Applies common plugins: `java`, `checkstyle`, `jacoco`, `spotless`, `errorprone`, and `pitest` (unless disabled).
- Configures Java toolchain, runtime launcher, and bytecode level (defaults to JDK 25 from any available vendor).
- The plugin itself targets Kotlin/JVM 17.
- Adds `mavenCentral()` by default and can optionally add `mavenLocal()` (both configurable).
- Sets Checkstyle tool version and uses project-level Checkstyle files when provided (bundled defaults otherwise).
- Sets JaCoCo tool version and enforces instruction coverage.
- Configures PIT mutation testing (`pitest` task, 95 % mutation threshold) for the resolved base package; it is not part of `check`.
- Configures Spotless for basic Java formatting (unused imports, import order, trailing whitespace, newline at EOF).
- Applies Spotless license header conventions when `LICENSE_HEADER` exists in the project root.
- Adds common compile/test dependencies (Lombok, JSpecify, JetBrains annotations, Error Prone/NullAway, JUnit Jupiter, AssertJ).
- Configures all `Test` tasks to use JUnit Platform and adds JUnit Platform launcher as `testRuntimeOnly`.
- Enables `sourcesJar` and `javadocJar` generation.
- Adds `maven-publish` conventions by default (`mavenJava` publication + optional GitHub Packages repository).
- Resolves `leanish.conventions.basePackage` from project config or infers it from `src/main/java` package declarations.
- Adds root-only helper tasks (`installGitHooks`, `setupProject`) and makes `build` depend on `installGitHooks`.
- Makes `check` depend on every `JacocoCoverageVerification` task.
- Declares configuration cache support on the Gradle Plugin Portal (isolated projects are not declared).

## How to use
Use the Gradle Plugin Portal for released versions.
The released examples below use `0.6.3`, the latest published version.

The plugin adds `mavenCentral()` by default to every project where it is applied.
The canonical plugin id is `io.github.leanish.java-conventions`.

### Single-project build
`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.java-conventions") version "0.6.3"
}
```

### Multi-project build
`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.github.leanish.java-conventions") version "0.6.3"
    }
}
```

Apply it in each `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.java-conventions")
}
```

### Local development before release
If the plugin version is not published to the Gradle Plugin Portal yet:

1. Publish this plugin locally:
   ```bash
   ./gradlew publishToMavenLocal
   ```
   This publishes the `version` from `build.gradle.kts`; request that exact version from the consumer.
2. Ensure consumer `settings.gradle(.kts)` has `mavenLocal()` in `pluginManagement.repositories` (before remote repositories) while testing local builds, for example:
   ```kotlin
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
           mavenCentral()
       }
   }
   ```
3. Do not use `pluginManagement { includeBuild("../java-conventions") }`; consume by plugin id + version.
4. When you switch back to a released version, remove `mavenLocal()` (or move it after remote repositories) to avoid resolving stale local artifacts.

If you want root-only tasks (`installGitHooks`, `setupProject`) in a multi-project build, apply the plugin in the root project too:

```kotlin
plugins {
    id("io.github.leanish.java-conventions") version "0.6.3"
}
```

## Convention properties
Configure behavior through `gradle.properties` (or `-P...`):

```properties
# Repository conventions
leanish.conventions.repositories.mavenLocal.enabled=false
leanish.conventions.repositories.mavenCentral.enabled=true

# Publishing conventions
leanish.conventions.publishing.enabled=true
leanish.conventions.publishing.githubPackages.enabled=true
leanish.conventions.publishing.githubOwner=acme
leanish.conventions.publishing.developer.id=acme
leanish.conventions.publishing.developer.name=Acme Team
leanish.conventions.publishing.developer.url=https://github.com/acme

# Project conventions (optional override)
leanish.conventions.basePackage=io.github.leanish

# Mutation testing conventions
leanish.conventions.pitest.enabled=true

```

Environment variables are also supported, and they override `gradle.properties` / `-P` values:
- `JAVA_CONVENTIONS_MAVEN_LOCAL_ENABLED`
- `JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED`
- `JAVA_CONVENTIONS_PITEST_ENABLED`
- `JAVA_CONVENTIONS_PUBLISHING_ENABLED`
- `JAVA_CONVENTIONS_PUBLISHING_GITHUB_PACKAGES_ENABLED`
- `JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_ID`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_NAME`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_URL`
- `JAVA_CONVENTIONS_BASE_PACKAGE`
- `GITHUB_REPOSITORY_OWNER` (highest precedence for publishing owner inference)

Wrapper plugins can import these names directly from
`io.github.leanish.gradleconventions.ConventionProperties` instead of duplicating string literals.

`leanish.conventions.basePackage` is optional:
- If configured, the plugin uses that value.
- If missing, the plugin infers it from `src/main/java` package declarations, stores the inferred
  value in project extra properties, and logs the inference.
- The plugin fails fast only when the property is blank or when it cannot infer any Java package.

Publishing repository/name/description conventions are derived from project metadata and are not configurable properties:
- GitHub repository defaults to `project.name`
- POM name defaults to `project.name`
- POM description defaults to `project.description` (or `project.name` when missing)

Publishing owner/developer metadata is optional:
- `leanish.conventions.publishing.githubOwner` resolves owner for GitHub URLs/repository.
- Owner resolves by `GITHUB_REPOSITORY_OWNER`, then `JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER`, then `leanish.conventions.publishing.githubOwner`, then `group` when it matches `io.github.<owner>`.
- Developer fields (`id`, `name`, `url`) are optional and independent; missing values are inferred from resolved owner when possible (`id=<owner>`, `name=<owner>`, `url=https://github.com/<owner>`), and the `developers` block is emitted only when all three resolve.
- GitHub Packages credentials resolve by environment first (`GITHUB_ACTOR`, `GITHUB_TOKEN`), then properties (`gpr.user`, `gpr.key`).

## Publishing
- For Gradle Plugin Portal, set `gradle.publish.key` and `gradle.publish.secret` in `~/.gradle/gradle.properties`,
  or use `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`.
- Publish with `./gradlew publishPlugins`.
- For local testing, use `./gradlew publishToMavenLocal`.
- For GitHub Packages publishing of this plugin project:
  - repository is `https://maven.pkg.github.com/leanish/java-conventions`
  - credentials resolve by environment first (`GITHUB_ACTOR`, `GITHUB_TOKEN`), then properties (`gpr.user`, `gpr.key`)
  - publish with `./gradlew publishAllPublicationsToGitHubPackagesRepository`.

## Override patterns
### Override existing values
Replace defaults with your own:

```kotlin
tasks.withType<JacocoCoverageVerification>().configureEach {
    violationRules {
        rules.forEach { rule ->
            rule.limits.forEach { limit ->
                limit.minimum = "0.83".toBigDecimal()
            }
        }
    }
}
```

### Add to defaults
Keep defaults and append more:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        errorproneArgs.add("-Xep:MissingOverride:WARN")
    }
}
```

### Reset and replace
Clear defaults, then define your own (google-java-format `1.37.0` needs Gradle running on JDK 21+):

```kotlin
spotless {
    java {
        clearSteps()
        googleJavaFormat("1.37.0")
        endWithNewline()
    }
}
```

## JDK toolchain
- Default JDK version is 25; vendor is `ANY`.
- To change the vendor without changing the version:

```kotlin
java {
    toolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}
```

- To change bytecode level:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
```

## Sources and Javadocs artifacts
- The plugin enables `sourcesJar` and `javadocJar` by default.
- Consumers can disable them in `build.gradle.kts`:

```kotlin
tasks.named("sourcesJar") {
    enabled = false
}

tasks.named("javadocJar") {
    enabled = false
}
```

- If you also want to omit these artifacts from publications, skip the `sourcesElements` and `javadocElements` variants:

```kotlin
components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations["sourcesElements"]) { skip() }
    withVariantsFromConfiguration(configurations["javadocElements"]) { skip() }
}
```

## Coverage behavior
- Enforces instruction coverage via `jacocoTestCoverageVerification`.
- Default minimum is `0.85` unless overridden.
- Set `-DexcludeTags=integration` (or any tags) to skip those tests and disable coverage verification.
  The same tags are excluded from mutation testing.

## Mutation testing
- Applies `info.solidsoft.pitest` (gradle-pitest-plugin `1.19.0`) with PIT `1.30.0` and its JUnit 5 plugin `1.2.3`.
- `./gradlew pitest` writes HTML and XML reports to `build/reports/pitest` (not timestamped).
- `targetClasses` and `targetTests` default to `<package>.*` for every package in the resolved `leanish.conventions.basePackage`,
  so narrowing `targetClasses` keeps running every test under the base package.
- `threads` defaults to the number of available processors.
- PIT runs on the same toolchain launcher as `Test` tasks. JVM arguments of `Test` tasks are not copied;
  set `pitest { jvmArgs = listOf(...) }` when the tests need them.
- In multi-project builds, every project that applies the plugin gets its own `pitest` task, which by default mutates only that
  project's classes. Projects without mutable code fail `pitest` (PIT's `failWhenNoMutations`); set
  `pitest { failWhenNoMutations = false }` there. Cross-project mutation and aggregated reports need extra gradle-pitest-plugin
  setup (`additionalMutableCodePaths`, the `info.solidsoft.pitest.aggregator` plugin).
- `mutationThreshold` defaults to `95`: `./gradlew pitest` fails when the mutation score (as PIT rounds it) is below 95 %.
  Override it per project, or set it to `0` to report without failing. `-DexcludeTags` leaves the threshold active.
- `pitest` is not wired into `check`, because a run takes noticeably longer than the tests. Run `./gradlew pitest` in a
  separate CI job; make that job optional for advisory feedback, or required to enforce the threshold.
- `leanish.conventions.pitest.enabled=false` (or `JAVA_CONVENTIONS_PITEST_ENABLED=false`) skips applying and configuring
  PIT: no `pitest` task, so a `pitest { }` block in the build script no longer compiles. A project that applies
  `info.solidsoft.pitest` itself while the switch is off gets the plugin's own defaults.
- Known warning: gradle-pitest-plugin `1.19.0` calls `Configuration.setVisible(boolean)`, which Gradle 9.1+ deprecates. On
  Gradle 9.8.0, builds that apply it end with "Deprecated Gradle features were used in this build, making it incompatible
  with Gradle 10." (`--warning-mode all` shows the call, scheduled for removal in Gradle 11). It is only a warning under the
  default warning mode, but `--warning-mode fail` turns it into a build failure. The fix is merged upstream
  ([szpak/gradle-pitest-plugin#398](https://github.com/szpak/gradle-pitest-plugin/pull/398)) and these conventions will move
  to the first plugin release that includes it. Turning PIT off removes the warning.

```kotlin
pitest {
    targetClasses = listOf("com.example.core.*")
    mutationThreshold = 90
}
```

## Dependency conventions
- Adds `org.jspecify:jspecify:1.0.1`, `org.jetbrains:annotations:26.1.0`, and
  `com.google.errorprone:error_prone_annotations:2.50.0` as `compileOnly` and `testCompileOnly`.
- Adds `org.projectlombok:lombok:1.18.48` as `compileOnly`, `testCompileOnly`,
  `annotationProcessor`, and `testAnnotationProcessor`.
- Sets a Guava `33.7.2-jre` version floor for Checkstyle and Error Prone to fix `CVE-2026-102554`.
  Consumer Checkstyle `toolVersion` overrides remain supported.
- Adds Error Prone analysis dependencies:
  - `com.google.errorprone:error_prone_core:2.50.0`
  - `com.uber.nullaway:nullaway:0.14.2`

## JUnit Platform
- All `Test` tasks call `useJUnitPlatform()`.
- The plugin adds `org.junit.jupiter:junit-jupiter:6.1.3` and `org.assertj:assertj-core:3.27.7` as `testImplementation`.
- The plugin adds `org.junit.platform:junit-platform-launcher:6.1.3` as `testRuntimeOnly`.
- If you need different test execution behavior for specific tasks, override those tasks in your build script.

## Maven Publish conventions
Publishing conventions are enabled by default. To disable, set
`leanish.conventions.publishing.enabled=false`.

When enabled, the plugin:
- Applies `maven-publish`.
- Creates/configures `mavenJava` publication from the Java component.
- Configures POM defaults from project metadata:
  - repository and SCM use `https://github.com/<githubOwner>/<project.name>` when owner is resolvable
  - POM `name` uses `project.name`
  - POM `description` uses `project.description` (or falls back to `project.name`)
- Adds GitHub Packages (`GitHubPackages`) publishing repository only when:
  - `leanish.conventions.publishing.githubPackages.enabled=true` (default), and
  - owner is resolvable.

### Overriding publishing
Two supported patterns:

1. Keep conventions enabled and reconfigure existing entries.
   - Reconfigure `mavenJava` with `publications.named("mavenJava", MavenPublication::class.java)`.
   - Reconfigure `GitHubPackages` with `repositories.named("GitHubPackages", MavenArtifactRepository::class.java)`.
   - Do not use `create<MavenPublication>("mavenJava")` when conventions are enabled, because `mavenJava` already exists.

2. Fully replace plugin publishing behavior.
   - Set `leanish.conventions.publishing.enabled=false`.
   - Configure `maven-publish` entirely in the consumer project (`publications { create(...) }`, custom repositories, full POM metadata).

## Import order
With the bundled Checkstyle configuration, Spotless orders imports the way its `ImportOrder` rule expects: static imports
first, then `java`, `javax`, `org`, `com` and everything else (for example `lombok`), one blank line between groups and
alphabetical within each. `spotlessApply` (and the pre-commit hook) fixes the order, so the IDE's import layout doesn't
matter. When the project has its own `config/checkstyle/checkstyle.xml`, the plugin adds no import-order step; add
`importOrder(...)` to `spotless { java { } }` to match that config.

## License header conventions
The plugin applies Spotless Java license headers only when `LICENSE_HEADER` exists in the project root.

When the file exists, the plugin:
- Applies Spotless Java license header step.
- Uses `LICENSE_HEADER` from the project root.

When `LICENSE_HEADER` is missing, the plugin skips the header step and logs at `INFO` level.
There is no separate `leanish.conventions.spotless.licenseHeader.enabled` property; file presence controls behavior.

## Error Prone
The conventions plugin applies `net.ltgt.errorprone` and adds Error Prone + NullAway dependencies automatically.
It:
- Adds `-XDaddTypeAnnotationsToSymbol=true`.
- Configures Error Prone with default arguments (including NullAway).
- Sets NullAway annotated packages from resolved `leanish.conventions.basePackage`.
- Disables Error Prone for `compileTestJava`.

> **Runtime requirement:** Error Prone runs as a `javac` annotation processor and requires the **Java compilation toolchain** to be JDK 21 or newer. The default JDK 25 toolchain satisfies this. Overriding the toolchain below JDK 21 (e.g. `java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }`) will cause compilation to fail.

> **Fail-fast validation:** The plugin validates the configured Java toolchain during configuration and fails early with a descriptive error when `languageVersion < 21`.

## Notes
- Checkstyle uses tool version `14.3.0`.
- Checkstyle uses `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml` when present in the consumer project.
- If either file is missing, the plugin falls back to bundled defaults (`checkstyle.xml` and empty suppressions).
- These files are materialized under `build/generated/checkstyle` for Checkstyle only and are not packaged into JARs/publications.
- **Checkstyle runtime requirement:** Checkstyle 14.x requires the **Java toolchain used by the plugin's runtime launcher** to be JDK 21 or newer. The default toolchain is JDK 25, which satisfies this. If you override the project Java toolchain to a version lower than JDK 21, Checkstyle tasks will fail even when the Gradle daemon itself runs on a newer JDK.
- The plugin does not add a toolchain resolver; ensure the configured JDK is available locally or add a resolver in the consuming project.
- Dependencies added by the plugin are additive; your project dependencies remain in effect.
- The bundled pre-commit hook runs `./gradlew spotlessApply` and `./gradlew checkstyleMain checkstyleTest`, and may modify files before commit.

## Plugin project CI (maintainers)
- `.github/workflows/ci.yml` runs the default build path (Kotlin compilation toolchain on JDK 17, Gradle runtime/toolchain tasks on JDK 25).
- `.github/workflows/testing-legacy-jdk.yml` runs a matrix on legacy runtime JDKs (`17`, `21`).
- The legacy matrix uses `-PjavaConventions.runtimeJdkVersion=<version>` to set the plugin project's own `Test`/`JavaExec` runtime launcher.
- `javaConventions.runtimeJdkVersion` is a plugin-project testing override only; it is not a consumer convention property.
- `.github/workflows/publishing-github.yml` requires both `ci.yml` and `testing-legacy-jdk.yml` jobs to pass before publishing.
