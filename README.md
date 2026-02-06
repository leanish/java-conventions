# gradle-conventions

Shared Gradle conventions for JDK-based projects.

## What it provides
- Applies common plugins: `java`, `checkstyle`, `jacoco`, `spotless`, `net.ltgt.errorprone`.
- Configures Java toolchain, runtime launcher, and bytecode level (defaults to JDK 25, any vendor).
- The plugin itself uses Kotlin/JVM 21 (Gradle embeds Kotlin 2.2.x today).
- Sets Checkstyle tool version and uses the bundled Checkstyle config (project suppressions are optional).
- Sets JaCoCo tool version and enforces instruction coverage.
- Configures Spotless for basic Java formatting (unused imports, trailing whitespace, newline at EOF).
- Adds common compile/test dependencies (Lombok, JSpecify, JetBrains annotations, Error Prone/NullAway).
- Adds root-only helper tasks (`installGitHooks`, `setupProject`) and makes `build` depend on `installGitHooks`.
- Makes `check` depend on every `JacocoCoverageVerification` task.

## How to use
Publish the plugin in Gradle Plugin Portal

### Single-project build
`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.gradle-conventions") version "0.2.0"
}
```

### Multi-project build
`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.github.leanish.gradle-conventions") version "0.2.0"
    }
}
```

Apply it in each `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.leanish.gradle-conventions")
}
```

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

## Error Prone
The conventions plugin applies `net.ltgt.errorprone` and adds Error Prone + NullAway dependencies automatically.
It:
- Adds `-XDaddTypeAnnotationsToSymbol=true`.
- Configures Error Prone with default arguments (including NullAway).
- Disables Error Prone for `compileTestJava`.

## Notes
- Checkstyle uses the configuration bundled in this plugin. If `config/checkstyle/suppressions.xml` exists, it is applied; otherwise no suppressions are used.
- The plugin does not add a toolchain resolver; ensure the configured JDK is available locally or add a resolver in the consuming project.
- Dependencies added by the plugin are additive; your project dependencies remain in effect.
