/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
import info.solidsoft.gradle.pitest.PitestPluginExtension
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
import io.github.leanish.gradleconventions.ConventionVersions
import io.github.leanish.gradleconventions.GitHooks
import io.github.leanish.gradleconventions.WriteCheckstyleConfigTask
import io.github.leanish.gradleconventions.javaConventionsProviders
import io.github.leanish.gradleconventions.stringProperty
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.CheckstyleExtension

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
val mavenLocalEnabled = conventionProviders.mavenLocalEnabled
val mavenCentralEnabled = conventionProviders.mavenCentralEnabled
val publishingConventionsEnabled = conventionProviders.publishingConventionsEnabled
val publishingGithubPackagesEnabled = conventionProviders.publishingGithubPackagesEnabled
val publishingGithubOwner = conventionProviders.publishingGithubOwner
val publishingGithubRepository = conventionProviders.publishingGithubRepository
val publishingPomName = conventionProviders.publishingPomName
val publishingPomDescription = conventionProviders.publishingPomDescription
val nullAwayAnnotatedPackages = conventionProviders.nullAwayAnnotatedPackages
val pitestEnabled = conventionProviders.pitestEnabled
val pitestTargetClasses = conventionProviders.pitestTargetClasses
val checkstyleConfigDir = conventionProviders.checkstyleConfigDir
val checkstyleConfigFile = conventionProviders.checkstyleConfigFile
val runtimeLauncher = conventionProviders.runtimeLauncher

val defaultJdkVersion = 25
val minimumSupportedToolchainVersion = 21
java {
    // Keep in sync with the release flag for IDE/tooling metadata; javac uses options.release.
    sourceCompatibility = JavaVersion.toVersion(defaultJdkVersion)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(defaultJdkVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

afterEvaluate {
    val configuredToolchainVersion = java.toolchain.languageVersion.orNull?.asInt()
    if (configuredToolchainVersion != null && configuredToolchainVersion < minimumSupportedToolchainVersion) {
        throw GradleException(
            "Java toolchain languageVersion must be >= $minimumSupportedToolchainVersion. " +
                "Configured: $configuredToolchainVersion. " +
                "Checkstyle 14.x and Error Prone require toolchain JDK 21+.",
        )
    }
}

repositories {
    if (mavenLocalEnabled.get()) {
        mavenLocal()
    }
    if (mavenCentralEnabled.get()) {
        mavenCentral()
    }
}

val checkstyleExtension = extensions.getByType<CheckstyleExtension>()

dependencies {
    checkstyle("com.google.guava:guava:${ConventionVersions.GUAVA}") {
        because("CVE-2026-102554: prevents excessive allocation during Guava deserialization")
    }
    // Adding a dependency disables Checkstyle's default dependency; retain toolVersion overrides.
    checkstyle(providers.provider {
        "com.puppycrawl.tools:checkstyle:${checkstyleExtension.toolVersion}"
    })
    errorprone("com.google.guava:guava:${ConventionVersions.GUAVA}") {
        because("CVE-2026-102554: prevents excessive allocation during Guava deserialization")
    }

    compileOnly("org.jspecify:jspecify:${ConventionVersions.JSPECIFY}")
    testCompileOnly("org.jspecify:jspecify:${ConventionVersions.JSPECIFY}")
    compileOnly("org.jetbrains:annotations:${ConventionVersions.JETBRAINS_ANNOTATIONS}")
    testCompileOnly("org.jetbrains:annotations:${ConventionVersions.JETBRAINS_ANNOTATIONS}")
    compileOnly("com.google.errorprone:error_prone_annotations:${ConventionVersions.ERROR_PRONE}")
    testCompileOnly("com.google.errorprone:error_prone_annotations:${ConventionVersions.ERROR_PRONE}")
    compileOnly("org.projectlombok:lombok:${ConventionVersions.LOMBOK}")
    testCompileOnly("org.projectlombok:lombok:${ConventionVersions.LOMBOK}")
    annotationProcessor("org.projectlombok:lombok:${ConventionVersions.LOMBOK}")
    testAnnotationProcessor("org.projectlombok:lombok:${ConventionVersions.LOMBOK}")
    testImplementation("org.junit.jupiter:junit-jupiter:${ConventionVersions.JUNIT}")
    testImplementation("org.assertj:assertj-core:${ConventionVersions.ASSERTJ}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${ConventionVersions.JUNIT}")

    errorprone("com.google.errorprone:error_prone_core:${ConventionVersions.ERROR_PRONE}")
    errorprone("com.uber.nullaway:nullaway:${ConventionVersions.NULLAWAY}")
}

spotless {
    java {
        removeUnusedImports()
        // Same order as the bundled Checkstyle ImportOrder rule (static imports, then java, javax, org, com, everything
        // else), so spotlessApply fixes what Checkstyle would reject. A project's own Checkstyle config keeps its order.
        if (!rootProject.file("config/checkstyle/checkstyle.xml").exists()) {
            importOrder("\\#", "java", "javax", "org", "com", "")
        }
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
            val existingGithubPackages = findByName("GitHubPackages")
            if (publishingGithubPackagesEnabled.get() && resolvedGithubOwner != null) {
                when (existingGithubPackages) {
                    is MavenArtifactRepository -> {
                        // Consumer already configured a Maven GitHubPackages repository; keeping it.
                    }
                    null -> maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/$resolvedGithubOwner/$resolvedGithubRepository")
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
                    else -> throw GradleException(
                        "Repository 'GitHubPackages' exists but is not a MavenArtifactRepository " +
                            "(${existingGithubPackages::class.java.name})",
                    )
                }
            }
        }
    }
}

private val writeCheckstyleConfig = tasks.register<WriteCheckstyleConfigTask>("writeCheckstyleConfig") {
    description = "Writes bundled Checkstyle configuration to the build directory"
    outputDir.set(checkstyleConfigDir)
    val consumerCheckstyleInputFile = rootProject.file("config/checkstyle/checkstyle.xml")
    val consumerSuppressionsInputFile = rootProject.file("config/checkstyle/suppressions.xml")
    if (consumerCheckstyleInputFile.exists()) {
        consumerCheckstyleFile.set(consumerCheckstyleInputFile)
    }
    if (consumerSuppressionsInputFile.exists()) {
        consumerSuppressionsFile.set(consumerSuppressionsInputFile)
    }
}

checkstyle {
    toolVersion = ConventionVersions.CHECKSTYLE
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    dependsOn(writeCheckstyleConfig)
    configDirectory.set(checkstyleConfigDir)
    configFile = checkstyleConfigFile.get()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = ConventionVersions.JACOCO
}

if (pitestEnabled.get()) {
    pluginManager.apply("info.solidsoft.pitest")
}

plugins.withId("info.solidsoft.pitest") {
    if (!pitestEnabled.get()) {
        return@withId
    }

    extensions.configure<PitestPluginExtension> {
        pitestVersion.set(ConventionVersions.PITEST)
        junit5PluginVersion.set(ConventionVersions.PITEST_JUNIT5_PLUGIN)
        targetClasses.set(pitestTargetClasses)
        targetTests.set(pitestTargetClasses)
        excludedGroups.set(excludedTags)
        threads.set(Runtime.getRuntime().availableProcessors())
        outputFormats.set(listOf("HTML", "XML"))
        timestampedReports.set(false)
        mutationThreshold.set(95)
    }
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
        GitHooks.directory(
            gitMarker = gitMarker.asFile,
            fallbackHooksDirectory = layout.projectDirectory.dir(".git/hooks").asFile,
        )
    }
    val writePreCommitHook = tasks.register("writePreCommitHook") {
        description = "Writes the bundled pre-commit hook to the build directory"
        outputs.file(preCommitHookFile)
        onlyIf { gitExists.get() && !projectHookFile.asFile.exists() }

        doLast {
            val targetFile = preCommitHookFile.get().asFile
            targetFile.parentFile.mkdirs()
            targetFile.writeText(GitHooks.bundledPreCommitHook())
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
