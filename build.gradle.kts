import org.gradle.plugin.compatibility.compatibility

plugins {
    alias(libs.plugins.kotlin.dsl)
    `maven-publish`
    jacoco
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.spotless)
}

group = "io.github.leanish"
version = "0.6.6-SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        // No Java sources today, but pinning Java toolchain tasks (especially tests) to 25 to mirror the conventions' default toolchain.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    // Targets JVM 17 so the plugin JAR can be loaded by Gradle running on JDK 17+.
    jvmToolchain(17)
}

dependencies {
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/** Writes the catalog versions the precompiled script injects into consumer builds as Kotlin constants. */
abstract class GenerateConventionVersions : DefaultTask() {
    @get:Input
    abstract val versions: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.deleteRecursively()
        val file = outputDir.resolve("io/github/leanish/gradleconventions/ConventionVersions.kt")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package io.github.leanish.gradleconventions")
                appendLine()
                appendLine("// Generated from gradle/libs.versions.toml by the generateConventionVersions task; do not edit.")
                appendLine("internal object ConventionVersions {")
                versions.get().toSortedMap().forEach { (name, version) ->
                    appendLine("    const val $name = \"$version\"")
                }
                appendLine("}")
            },
        )
    }
}

val generateConventionVersions = tasks.register<GenerateConventionVersions>("generateConventionVersions") {
    versions.set(
        mapOf(
            "ASSERTJ" to libs.versions.assertj,
            "CHECKSTYLE" to libs.versions.checkstyle,
            "COMMONMARK" to libs.versions.commonmark,
            "ERROR_PRONE" to libs.versions.errorprone.asProvider(),
            "GUAVA" to libs.versions.guava,
            "JACOCO" to libs.versions.jacoco,
            "JETBRAINS_ANNOTATIONS" to libs.versions.jetbrains.annotations,
            "JSPECIFY" to libs.versions.jspecify,
            "JUNIT" to libs.versions.junit,
            "LOMBOK" to libs.versions.lombok,
            "NULLAWAY" to libs.versions.nullaway,
            "PITEST" to libs.versions.pitest.asProvider(),
            "PITEST_JUNIT5_PLUGIN" to libs.versions.pitest.junit5.plugin,
            // Not injected; lets the tests request the same gradle-pitest-plugin version the conventions apply.
            "PITEST_GRADLE_PLUGIN" to libs.versions.pitest.gradle.plugin,
        ).mapValues { (_, version) -> version.get() },
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/conventionVersions/kotlin"))
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateConventionVersions)
}

// What the conventions inject into consumer builds, resolved here too, so this repository's supply-chain gate scans the
// exact versions consumers get. Each mirrors the consumer configuration it's named after, with the same floors; nothing
// compiles against them. JaCoCo needs no mirror: this build applies it with the same tool version.
// The consumer configurations each mirror covers; ConventionMirrorsTest compares them with a consumer build.
val mirroredConsumerConfigurations = mutableMapOf<String, List<String>>()

fun conventionMirror(name: String, vararg consumerConfigurations: String): Configuration {
    mirroredConsumerConfigurations[name] = consumerConfigurations.toList()
    return configurations.create(name) {
        description = "What the conventions add to consumer builds' ${consumerConfigurations.joinToString(" and ")}, for the supply-chain gate."
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

val conventionCheckstyle = conventionMirror("conventionCheckstyle", "checkstyle")
val conventionErrorprone = conventionMirror("conventionErrorprone", "errorprone")
val conventionCompileOnly = conventionMirror("conventionCompileOnly", "compileOnly", "testCompileOnly")
val conventionAnnotationProcessor = conventionMirror("conventionAnnotationProcessor", "annotationProcessor", "testAnnotationProcessor")
val conventionTestImplementation = conventionMirror("conventionTestImplementation", "testImplementation")
val conventionTestRuntimeOnly = conventionMirror("conventionTestRuntimeOnly", "testRuntimeOnly")
val conventionPitest = conventionMirror("conventionPitest", "pitest")
val conventionMirrors = listOf(
    conventionCheckstyle,
    conventionErrorprone,
    conventionCompileOnly,
    conventionAnnotationProcessor,
    conventionTestImplementation,
    conventionTestRuntimeOnly,
    conventionPitest,
)

// Same floor, and same reason, as the plugin sets in consumer builds.
val guavaFloorReason = "CVE-2026-102554 and GHSA-v5x9-67m3-7m56: excessive allocation in Guava deserialization, hash flooding"
val commonmarkTablesFloorReason = "GHSA-cx98-gqcg-rp8q: denial of service parsing Markdown tables"

dependencies {
    conventionCheckstyle(libs.checkstyle)
    conventionCheckstyle(libs.guava) {
        because(guavaFloorReason)
    }
    conventionErrorprone(libs.errorprone.core)
    conventionErrorprone(libs.nullaway)
    conventionErrorprone(libs.guava) {
        because(guavaFloorReason)
    }
    conventionErrorprone(libs.commonmark.ext.gfm.tables) {
        because(commonmarkTablesFloorReason)
    }
    conventionCompileOnly(libs.jspecify)
    conventionCompileOnly(libs.jetbrains.annotations)
    conventionCompileOnly(libs.errorprone.annotations)
    conventionCompileOnly(libs.lombok)
    conventionAnnotationProcessor(libs.lombok)
    conventionTestImplementation(libs.junit.jupiter)
    conventionTestImplementation(libs.assertj.core)
    conventionTestRuntimeOnly(libs.junit.platform.launcher)
    conventionPitest(libs.pitest.command.line)
    conventionPitest(libs.pitest.junit5.plugin)
}

/**
 * Writes each convention mirror's consumer configurations and declared coordinates (group:artifact:version) as a test
 * resource, so ConventionMirrorsTest can compare them with what the plugin declares in a consumer build.
 */
abstract class WriteConventionMirrors : DefaultTask() {
    @get:Input
    abstract val mirrors: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val file = outputDirectory.file("convention-mirrors.properties").get().asFile
        file.parentFile.mkdirs()
        file.writeText(mirrors.get().toSortedMap().entries.joinToString("") { (name, value) -> "$name=$value\n" })
    }
}

val writeConventionMirrors = tasks.register<WriteConventionMirrors>("writeConventionMirrors") {
    mirrors.set(
        provider {
            conventionMirrors.associate { mirror ->
                val coordinates = mirror.dependencies.map { "${it.group}:${it.name}:${it.version}" }.sorted()
                mirror.name to mirroredConsumerConfigurations.getValue(mirror.name).joinToString(",") + "|" + coordinates.joinToString(",")
            }
        },
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/resources/conventionMirrors"))
}

sourceSets.test {
    resources.srcDir(writeConventionMirrors)
}

val defaultRuntimeJavaVersion = 25
val runtimeJavaVersion = providers.gradleProperty("javaConventions.runtimeJdkVersion")
    .map(String::toInt)
    .orElse(defaultRuntimeJavaVersion)
val runtimeLauncher = project.extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(runtimeJavaVersion.map(JavaLanguageVersion::of))
}

val coverageIncludes = listOf("io/github/leanish/gradleconventions/**")

tasks.withType<JavaExec>().configureEach {
    // Keep runtime Java version aligned with legacy workflow matrix checks.
    javaLauncher.set(runtimeLauncher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Keep test Java version aligned with legacy workflow matrix checks.
    javaLauncher.set(runtimeLauncher)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.asFileTree.matching {
                include(coverageIncludes)
            },
        ),
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.asFileTree.matching {
                include(coverageIncludes)
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

spotless {
    kotlin {
        target("src/main/kotlin/**/*.kt")
        licenseHeaderFile("LICENSE_HEADER", "^(package|import|@file:)")
    }
    kotlinGradle {
        target("src/main/kotlin/**/*.gradle.kts")
        licenseHeaderFile("LICENSE_HEADER", "^(import|plugins|buildscript)")
    }
}

gradlePlugin {
    website.set("https://github.com/leanish/java-conventions")
    vcsUrl.set("https://github.com/leanish/java-conventions")
    plugins {
        val pluginTags = listOf(
            "conventions",
            "java",
            "checkstyle",
            "spotless",
            "junit",
            "coverage",
            "jacoco",
            "errorprone",
            "nullaway",
            "mutation-testing",
            "license",
            "git-hooks",
            "publishing",
            "maven-publish",
        )

        named("io.github.leanish.java-conventions") {
            displayName = "Leanish Java Conventions"
            description = "Shared Gradle conventions for Java projects."
            tags.set(pluginTags)
            // Backed by ConfigurationCacheCompatibilityTest; isolated projects stay undeclared until tested.
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

publishing {
    repositories {
        // The publishing workflow's prepare job stages the publications here; its publish job uploads exactly these files.
        maven {
            name = "Staging"
            url = uri(layout.buildDirectory.dir("publication-staging"))
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/leanish/java-conventions")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR")
                    .orElse(providers.gradleProperty("gpr.user"))
                    .orNull
                password = providers.environmentVariable("GITHUB_TOKEN")
                    .orElse(providers.gradleProperty("gpr.key"))
                    .orNull
            }
        }
    }
}
