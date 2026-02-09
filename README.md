# gradle-conventions

Shared Gradle conventions for JDK-based projects.

## What it provides
- Applies common plugins: `java`, `checkstyle`, `jacoco`, `spotless`, `errorprone`.
- Configures Java toolchain, runtime launcher, and bytecode level (defaults to JDK 25 from any available vendor).
- The plugin itself uses Kotlin/JVM 21 (Gradle embeds Kotlin 2.2.x today).
- Adds `mavenCentral()` by default (configurable).
- Sets Checkstyle tool version and uses the bundled Checkstyle config (project suppressions are optional).
- Sets JaCoCo tool version and enforces instruction coverage.
- Configures Spotless for basic Java formatting (unused imports, trailing whitespace, newline at EOF).
- Applies Spotless license header conventions when `LICENSE_HEADER` exists in the project root.
- Adds common compile/test dependencies (Lombok, JSpecify, JetBrains annotations, Error Prone/NullAway).
- Configures all `Test` tasks to use JUnit Platform and adds `org.junit.platform:junit-platform-launcher:6.0.2` as `testRuntimeOnly`.
- Adds `maven-publish` conventions by default (`mavenJava` publication + `mavenLocal`/GitHub Packages repositories).
- Adds root-only helper tasks (`installGitHooks`, `setupProject`) and makes `build` depend on `installGitHooks`.
- Makes `check` depend on every `JacocoCoverageVerification` task.

## How to use
Publish the plugin to the Gradle Plugin Portal (recommended).

The plugin adds `mavenCentral()` by default to every project where it is applied.

### Single-project build
`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.gradle-conventions") version "0.3.0"
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
        id("io.github.leanish.gradle-conventions") version "0.3.0"
    }
}
```

Apply it in each `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.gradle-conventions")
}
```

### Local development before release
If the plugin version is not published to the Gradle Plugin Portal yet:

1. Publish this plugin locally:
   ```bash
   ./gradlew publishToMavenLocal
   ```
2. Ensure consumer `settings.gradle(.kts)` has `mavenLocal()` in `pluginManagement.repositories` (before remote repositories), for example:
   ```kotlin
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
           mavenCentral()
       }
   }
   ```
3. Do not use `pluginManagement { includeBuild("../gradle-conventions") }`; consume by plugin id + version.

If you want root-only tasks (`installGitHooks`, `setupProject`) in a multi-project build, apply the plugin in the root project too:

```kotlin
plugins {
    id("io.github.leanish.gradle-conventions") version "0.3.0"
}
```

## Convention properties
Configure behavior through `gradle.properties` (or `-P...`):

```properties
# Repository conventions
leanish.conventions.repositories.mavenCentral.enabled=true

# Publishing conventions
leanish.conventions.publishing.enabled=true
leanish.conventions.publishing.githubOwner=leanish

```

Publishing repository/name/description conventions are derived from project metadata and are not configurable properties:
- GitHub repository defaults to `project.name`
- POM name defaults to `project.name`
- POM description defaults to `project.description` (or `project.name` when missing)

## Publishing
- For Gradle Plugin Portal, set `gradle.publish.key` and `gradle.publish.secret` in `~/.gradle/gradle.properties`,
  or use `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`.
- Publish with `./gradlew publishPlugins`.
- For local testing, use `./gradlew publishToMavenLocal`.

## Override patterns
### Override existing values
Replace defaults with your own:

```kotlin
tasks.withType<JacocoCoverageVerification>().configureEach {
    violationRules {
        rules.forEach { rule ->
            rule.limits.forEach { limit ->
                limit.minimum = "0.91".toBigDecimal()
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

## Coverage behavior
- Enforces instruction coverage via `jacocoTestCoverageVerification`.
- Default minimum is `0.85` unless overridden.
- Set `-DexcludeTags=integration` (or any tags) to skip those tests and disable coverage verification.

## JUnit Platform
- All `Test` tasks call `useJUnitPlatform()`.
- The plugin adds `org.junit.platform:junit-platform-launcher:6.0.2` as `testRuntimeOnly`.
- If you need different test execution behavior for specific tasks, override those tasks in your build script.

## Maven Publish conventions
Publishing conventions are enabled by default. To disable, set
`leanish.conventions.publishing.enabled=false`.

When enabled, the plugin:
- Applies `maven-publish`.
- Creates/configures `mavenJava` publication from the Java component.
- Configures POM defaults from project metadata:
  - repository and SCM use `https://github.com/<githubOwner>/<project.name>`
  - POM `name` uses `project.name`
  - POM `description` uses `project.description` (or falls back to `project.name`)
- Adds `mavenLocal()` and GitHub Packages (`GitHubPackages`) publishing repositories.

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
- Sets NullAway annotated packages to `io.github.leanish` by default.
- Disables Error Prone for `compileTestJava`.

To override the default NullAway package configuration:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        errorproneArgs.removeAll { it.startsWith("-XepOpt:NullAway:AnnotatedPackages=") }
        errorproneArgs.add("-XepOpt:NullAway:AnnotatedPackages=com.example")
    }
}
```

## Notes
- Checkstyle uses the configuration bundled in this plugin. If `config/checkstyle/suppressions.xml` exists, it is applied; otherwise no suppressions are used.
- The plugin does not add a toolchain resolver; ensure the configured JDK is available locally or add a resolver in the consuming project.
- The plugin adds `mavenCentral()` by default; disable it with `leanish.conventions.repositories.mavenCentral.enabled=false`.
- Publishing conventions use GitHub owner `leanish` by default; change with `leanish.conventions.publishing.githubOwner`.
- Dependencies added by the plugin are additive; your project dependencies remain in effect.
- The bundled pre-commit hook runs `./gradlew spotlessApply` and `./gradlew checkstyleMain checkstyleTest`, and may modify files before commit.
