# java-conventions

Shared Gradle conventions for JDK-based projects.

## What it provides
- Applies common plugins: `java`, `checkstyle`, `jacoco`, `spotless`, `errorprone`.
- Configures Java toolchain, runtime launcher, and bytecode level (defaults to JDK 25 from any available vendor).
- The plugin itself uses Kotlin/JVM 21 (Gradle embeds Kotlin 2.2.x today).
- Adds `mavenCentral()` by default (configurable).
- Sets Checkstyle tool version and uses project-level Checkstyle files when provided (bundled defaults otherwise).
- Sets JaCoCo tool version and enforces instruction coverage.
- Configures Spotless for basic Java formatting (unused imports, trailing whitespace, newline at EOF).
- Applies Spotless license header conventions when `LICENSE_HEADER` exists in the project root.
- Adds common compile/test dependencies (Lombok, JSpecify, JetBrains annotations, Error Prone/NullAway, JUnit Jupiter, AssertJ).
- Configures all `Test` tasks to use JUnit Platform and adds JUnit Platform launcher as `testRuntimeOnly`.
- Enables `sourcesJar` and `javadocJar` generation.
- Adds `maven-publish` conventions by default (`mavenJava` publication + `mavenLocal`/GitHub Packages repositories).
- Resolves `leanish.conventions.basePackage` from project config or infers it from `src/main/java` package declarations.
- Adds root-only helper tasks (`installGitHooks`, `setupProject`) and makes `build` depend on `installGitHooks`.
- Makes `check` depend on every `JacocoCoverageVerification` task.

## How to use
Use the Gradle Plugin Portal for released versions.
For local development of unreleased changes, publish this plugin to `mavenLocal()` and use your snapshot version (for example, `0.3.1-SNAPSHOT`).

The plugin adds `mavenCentral()` by default to every project where it is applied.
The canonical plugin id is `io.github.leanish.java-conventions`.

### Single-project build
`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.java-conventions") version "0.3.0"
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
        id("io.github.leanish.java-conventions") version "0.3.0"
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
2. Ensure consumer `settings.gradle(.kts)` has `mavenLocal()` in `pluginManagement.repositories` (before remote repositories) while testing unreleased snapshots, for example:
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
    id("io.github.leanish.java-conventions") version "0.3.0"
}
```

## Convention properties
Configure behavior through `gradle.properties` (or `-P...`):

```properties
# Repository conventions
leanish.conventions.repositories.mavenCentral.enabled=true

# Publishing conventions
leanish.conventions.publishing.enabled=true
leanish.conventions.publishing.githubOwner=acme
leanish.conventions.publishing.developer.id=acme
leanish.conventions.publishing.developer.name=Acme Team
leanish.conventions.publishing.developer.url=https://github.com/acme

# Project conventions (optional override)
leanish.conventions.basePackage=io.github.leanish

```

Environment variables are also supported, and they override `gradle.properties` / `-P` values:
- `JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED`
- `JAVA_CONVENTIONS_PUBLISHING_ENABLED`
- `JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_ID`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_NAME`
- `JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_URL`
- `JAVA_CONVENTIONS_BASE_PACKAGE`
- `GITHUB_REPOSITORY_OWNER` (highest precedence for publishing owner inference)

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
Clear defaults, then define your own:

```kotlin
spotless {
    java {
        clearSteps()
        googleJavaFormat("1.23.0")
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

## JUnit Platform
- All `Test` tasks call `useJUnitPlatform()`.
- The plugin adds `org.junit.jupiter:junit-jupiter:6.0.3` and `org.assertj:assertj-core:3.27.7` as `testImplementation`.
- The plugin adds `org.junit.platform:junit-platform-launcher:6.0.3` as `testRuntimeOnly`.
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
- Adds `mavenLocal()` always.
- Adds GitHub Packages (`GitHubPackages`) publishing repository only when owner is resolvable.

### Overriding publishing
Two supported patterns:

1. Keep conventions enabled and reconfigure existing entries.
   - Reconfigure `mavenJava` with `publications.named("mavenJava", MavenPublication::class.java)`.
   - Reconfigure `GitHubPackages` with `repositories.named("GitHubPackages", MavenArtifactRepository::class.java)`.
   - Do not use `create<MavenPublication>("mavenJava")` when conventions are enabled, because `mavenJava` already exists.

2. Fully replace plugin publishing behavior.
   - Set `leanish.conventions.publishing.enabled=false`.
   - Configure `maven-publish` entirely in the consumer project (`publications { create(...) }`, custom repositories, full POM metadata).

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

## Notes
- Checkstyle uses `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml` when present in the consumer project.
- If either file is missing, the plugin falls back to bundled defaults (`checkstyle.xml` and empty suppressions).
- These files are materialized under `build/generated/checkstyle` for Checkstyle only and are not packaged into JARs/publications.
- The plugin does not add a toolchain resolver; ensure the configured JDK is available locally or add a resolver in the consuming project.
- Dependencies added by the plugin are additive; your project dependencies remain in effect.
- The bundled pre-commit hook runs `./gradlew spotlessApply` and `./gradlew checkstyleMain checkstyleTest`, and may modify files before commit.
