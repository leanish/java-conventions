/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_ACTOR_ENV
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_PACKAGES_KEY
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_PACKAGES_USER
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_TOKEN_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_ID
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_ID_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_NAME
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_NAME_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_URL
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_URL_ENV
import io.github.leanish.gradleconventions.javaConventionsProviders
import io.github.leanish.gradleconventions.stringProperty
import io.github.leanish.gradleconventions.PropertyParser
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
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

private val conventionProviders = javaConventionsProviders()
val mavenCentralEnabled = conventionProviders.mavenCentralEnabled
val publishingConventionsEnabled = conventionProviders.publishingConventionsEnabled
val publishingGithubOwner = conventionProviders.publishingGithubOwner
val publishingGithubRepository = conventionProviders.publishingGithubRepository
val publishingPomName = conventionProviders.publishingPomName
val publishingPomDescription = conventionProviders.publishingPomDescription
val nullAwayAnnotatedPackages = conventionProviders.nullAwayAnnotatedPackages
val checkstyleConfigDir = conventionProviders.checkstyleConfigDir
val checkstyleConfigFile = conventionProviders.checkstyleConfigFile
val checkstyleSuppressionsFile = conventionProviders.checkstyleSuppressionsFile
val runtimeLauncher = conventionProviders.runtimeLauncher

val defaultJdkVersion = 25
java {
    // Keep in sync with the release flag for IDE/tooling metadata; javac uses options.release.
    sourceCompatibility = JavaVersion.toVersion(defaultJdkVersion)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(defaultJdkVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    if (mavenCentralEnabled.get()) {
        mavenCentral()
    }
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    testCompileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.47.0")
    testCompileOnly("com.google.errorprone:error_prone_annotations:2.47.0")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.2")

    errorprone("com.google.errorprone:error_prone_core:2.47.0")
    errorprone("com.uber.nullaway:nullaway:0.13.1")
}

spotless {
    java {
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()

        val projectHeaderFile = rootProject.file("LICENSE_HEADER")
        if (projectHeaderFile.exists()) {
            licenseHeaderFile(projectHeaderFile)
        } else {
            logger.info(
                "LICENSE_HEADER was not found at ${projectHeaderFile.path}; skipping automatic Spotless license header configuration.",
            )
        }
    }
}

if (publishingConventionsEnabled.get()) {
    pluginManager.apply("maven-publish")
}

plugins.withId("maven-publish") {
    if (!publishingConventionsEnabled.get()) {
        return@withId
    }

    extensions.configure<PublishingExtension>("publishing") {
        val resolvedGithubOwner = publishingGithubOwner.get().takeIf(String::isNotBlank)
        val resolvedGithubRepository = publishingGithubRepository.get()

        publications {
            val javaComponent = components.findByName("java")
            val existingPublication = findByName("mavenJava")
            val publication = when (existingPublication) {
                is MavenPublication -> existingPublication
                null -> create("mavenJava", MavenPublication::class.java)
                else -> throw GradleException(
                    "Publication 'mavenJava' exists but is not a MavenPublication (${existingPublication::class.java.name})",
                )
            }

            if (javaComponent != null && existingPublication == null) {
                publication.from(javaComponent)
            }

            val githubRepoUrl = resolvedGithubOwner?.let { owner ->
                "https://github.com/$owner/$resolvedGithubRepository"
            }
            val githubScmUrl = githubRepoUrl?.let { "scm:git:$it.git" }
            val githubDeveloperScmUrl = resolvedGithubOwner?.let { owner ->
                "scm:git:ssh://git@github.com/$owner/$resolvedGithubRepository.git"
            }

            val configuredDeveloperId = stringProperty(
                PUBLISHING_DEVELOPER_ID,
                PUBLISHING_DEVELOPER_ID_ENV,
            )
            val configuredDeveloperName = stringProperty(
                PUBLISHING_DEVELOPER_NAME,
                PUBLISHING_DEVELOPER_NAME_ENV,
            )
            val configuredDeveloperUrl = stringProperty(
                PUBLISHING_DEVELOPER_URL,
                PUBLISHING_DEVELOPER_URL_ENV,
            )
            val configuredDeveloperFields = listOf(
                configuredDeveloperId,
                configuredDeveloperName,
                configuredDeveloperUrl,
            )
            if (configuredDeveloperFields.any { it != null } && configuredDeveloperFields.any { it == null }) {
                throw GradleException(
                    "Properties '${PUBLISHING_DEVELOPER_ID}', '${PUBLISHING_DEVELOPER_NAME}' and '${PUBLISHING_DEVELOPER_URL}' must be configured together",
                )
            }

            val resolvedDeveloperId = configuredDeveloperId ?: resolvedGithubOwner
            val resolvedDeveloperName = configuredDeveloperName ?: resolvedGithubOwner
            val resolvedDeveloperUrl = configuredDeveloperUrl ?: resolvedGithubOwner?.let { owner ->
                "https://github.com/$owner"
            }

            publication.pom {
                name.convention(publishingPomName)
                description.convention(publishingPomDescription)
                if (githubRepoUrl != null) {
                    url.convention(githubRepoUrl)
                }
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                if (resolvedDeveloperId != null && resolvedDeveloperName != null && resolvedDeveloperUrl != null) {
                    developers {
                        developer {
                            id.set(resolvedDeveloperId)
                            name.set(resolvedDeveloperName)
                            url.set(resolvedDeveloperUrl)
                        }
                    }
                }

                if (githubRepoUrl != null || githubScmUrl != null || githubDeveloperScmUrl != null) {
                    scm {
                        if (githubRepoUrl != null) {
                            url.convention(githubRepoUrl)
                        }
                        if (githubScmUrl != null) {
                            connection.convention(githubScmUrl)
                        }
                        if (githubDeveloperScmUrl != null) {
                            developerConnection.convention(githubDeveloperScmUrl)
                        }
                    }
                }
            }
        }

        repositories {
            if (findByName("mavenLocal") == null) {
                mavenLocal()
            }

            val existingGithubPackages = findByName("GitHubPackages")
            if (resolvedGithubOwner != null && existingGithubPackages !is MavenArtifactRepository) {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/$resolvedGithubOwner/${publishingGithubRepository.get()}")
                    credentials {
                        username = stringProperty(
                            GITHUB_PACKAGES_USER,
                            GITHUB_ACTOR_ENV,
                        )
                        password = stringProperty(
                            GITHUB_PACKAGES_KEY,
                            GITHUB_TOKEN_ENV,
                        )
                    }
                }
            }
        }
    }
}

val projectSuppressionsFile: File = rootProject.file("config/checkstyle/suppressions.xml")

val writeCheckstyleConfig: TaskProvider<Task> = tasks.register("writeCheckstyleConfig") {
    description = "Writes bundled Checkstyle configuration to the build directory"
    outputs.dir(checkstyleConfigDir)
    if (projectSuppressionsFile.exists()) {
        inputs.file(projectSuppressionsFile)
    }

    doLast {
        val outputDir = checkstyleConfigDir.get().asFile
        outputDir.mkdirs()

        val checkstyleResource = requireNotNull(
            PropertyParser::class.java.classLoader.getResource("checkstyle/checkstyle.xml"),
        ) {
            "Missing bundled Checkstyle configuration"
        }

        checkstyleConfigFile.get().writeText(checkstyleResource.readText())
        val suppressionsFile = checkstyleSuppressionsFile.get()
        if (projectSuppressionsFile.exists()) {
            suppressionsFile.writeText(projectSuppressionsFile.readText())
        } else {
            val emptySuppressionsResource = requireNotNull(
                PropertyParser::class.java.classLoader.getResource("checkstyle/empty-suppressions.xml"),
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
                "-XepOpt:NullAway:AnnotatedPackages=${nullAwayAnnotatedPackages.get()}",
            ),
        )
    }
}

tasks.named<JavaCompile>("compileTestJava").configure {
    options.errorprone.enabled.set(false)
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
            val resource = requireNotNull(PropertyParser::class.java.classLoader.getResource("git-hooks/pre-commit")) {
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
