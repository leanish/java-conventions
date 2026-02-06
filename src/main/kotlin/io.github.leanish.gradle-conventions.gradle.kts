import io.github.leanish.gradleconventions.PluginResources
import java.io.File
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.jvm.toolchain.JavaLanguageVersion
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    java
    checkstyle
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val excludedTags: List<String> = providers.systemProperty("excludeTags")
    .map { tags -> tags.split(',').map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(emptyList())
val defaultJdkVersion = 25

java {
    // Keep in sync with the release flag for IDE/tooling metadata; javac uses options.release.
    sourceCompatibility = JavaVersion.toVersion(defaultJdkVersion)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(defaultJdkVersion))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    testCompileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    errorprone("com.google.errorprone:error_prone_core:2.46.0")
    errorprone("com.uber.nullaway:nullaway:0.13.1")
}

spotless {
    java {
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val checkstyleConfigDir: Provider<Directory> = layout.buildDirectory.dir("generated/checkstyle")
val checkstyleConfigFile: Provider<File> = checkstyleConfigDir.map { it.file("checkstyle.xml").asFile }
val checkstyleSuppressionsFile: Provider<File> = checkstyleConfigDir.map { it.file("suppressions.xml").asFile }
val projectSuppressionsFile: File = rootProject.file("config/checkstyle/suppressions.xml")

val writeCheckstyleConfig: TaskProvider<Task> = tasks.register("writeCheckstyleConfig") {
    description = "Writes bundled Checkstyle configuration to the build directory"
    outputs.dir(checkstyleConfigDir)
    inputs.file(projectSuppressionsFile).optional()

    doLast {
        val outputDir = checkstyleConfigDir.get().asFile
        outputDir.mkdirs()

        val checkstyleResource = requireNotNull(javaClass.classLoader.getResource("checkstyle/checkstyle.xml")) {
            "Missing bundled Checkstyle configuration"
        }

        checkstyleConfigFile.get().writeText(checkstyleResource.readText())
        val suppressionsFile = checkstyleSuppressionsFile.get()
        if (projectSuppressionsFile.exists()) {
            suppressionsFile.writeText(projectSuppressionsFile.readText())
        } else {
            val emptySuppressionsResource = requireNotNull(
                javaClass.classLoader.getResource("checkstyle/empty-suppressions.xml"),
            ) {
                "Missing bundled empty Checkstyle suppressions"
            }
            suppressionsFile.writeText(emptySuppressionsResource.readText())
        }
    }
}

checkstyle {
    toolVersion = "12.1.2"
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    dependsOn(writeCheckstyleConfig)
    configDirectory.set(checkstyleConfigDir)
    doFirst {
        configFile = checkstyleConfigFile.get()
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.14"
}

val toolchainSpec = java.toolchain
val runtimeLauncher: Provider<JavaLauncher> = javaToolchains.launcherFor {
    languageVersion.set(toolchainSpec.languageVersion)
    vendor.set(toolchainSpec.vendor)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(runtimeLauncher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        if (excludedTags.isNotEmpty()) {
            excludeTags(*excludedTags.toTypedArray())
        }
    }
    javaLauncher.set(runtimeLauncher)
}

tasks.named<JacocoReport>("jacocoTestReport").configure {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(defaultJdkVersion)

    // Required from errorprone 2.46.0+ on JDK 21
    options.compilerArgs.add("-XDaddTypeAnnotationsToSymbol=true")
    options.errorprone {
        // Skip Error Prone warnings for generated code (Lombok, MapStruct, etc.).
        disableWarningsInGeneratedCode.set(true)
        errorproneArgs.addAll(
            listOf(
                "-Xep:NullAway:ERROR",
                "-XepOpt:NullAway:AnnotatedPackages=io.github.leanish",
            ),
        )
    }
}

tasks.named<JavaCompile>("compileTestJava").configure {
    options.errorprone.isEnabled = false
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    enabled = excludedTags.isEmpty()
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

if (project == rootProject) {
    val gitMarker = layout.projectDirectory.file(".git")
    val projectHookFile = layout.projectDirectory.file("scripts/git-hooks/pre-commit")
    val preCommitHookFile = layout.buildDirectory.file("generated/git-hooks/pre-commit")
    val gitExists = providers.provider { gitMarker.asFile.exists() }
    val hooksDir = providers.provider {
        val markerFile = gitMarker.asFile
        if (markerFile.isDirectory) {
            return@provider markerFile.resolve("hooks")
        }

        val hooksPath = runCatching {
            val process = ProcessBuilder("git", "rev-parse", "--git-path", "hooks")
                .directory(layout.projectDirectory.asFile)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) output.ifBlank { null } else null
        }.getOrNull()

        if (!hooksPath.isNullOrBlank()) {
            val resolvedPath = File(hooksPath)
            return@provider if (resolvedPath.isAbsolute) {
                resolvedPath
            } else {
                layout.projectDirectory.file(hooksPath).asFile
            }
        }

        layout.projectDirectory.dir(".git/hooks").asFile
    }
    val writePreCommitHook = tasks.register("writePreCommitHook") {
        description = "Writes the bundled pre-commit hook to the build directory"
        outputs.file(preCommitHookFile)
        onlyIf { gitExists.get() && !projectHookFile.asFile.exists() }

        doLast {
            val resource = requireNotNull(PluginResources::class.java.classLoader.getResource("git-hooks/pre-commit")) {
                "Missing bundled pre-commit hook resource"
            }
            val targetFile = preCommitHookFile.get().asFile
            targetFile.parentFile.mkdirs()
            targetFile.writeText(resource.readText())
        }
    }

    val hookSource = providers.provider {
        val customHook = projectHookFile.asFile
        if (customHook.exists()) {
            customHook
        } else {
            preCommitHookFile.get().asFile
        }
    }

    tasks.register<Copy>("installGitHooks") {
        description = "Copies git hooks from the conventions plugin to .git/hooks"
        dependsOn(writePreCommitHook)
        onlyIf { gitExists.get() }

        from(hookSource) {
            rename { "pre-commit" }
            filePermissions {
                unix("755")
            }
        }
        into(hooksDir)
    }

    tasks.named("build") {
        dependsOn("installGitHooks")
    }

    tasks.register("setupProject") {
        description = "Sets up the project with git hooks and initial configuration"
        dependsOn("installGitHooks")

        doLast {
            println("Project setup completed!")
            println("Git hooks installed in .git/hooks/")
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<JacocoCoverageVerification>())
}
