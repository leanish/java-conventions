import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `kotlin-dsl`
    `maven-publish`
    jacoco
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.diffplug.spotless") version "8.2.1"
}

group = "io.github.leanish"
version = "0.3.0-SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        // Build/test the plugin on Java 25 to mirror the conventions' default toolchain.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    // Gradle embeds Kotlin 2.2.x today; pinning it to JDK 21 until Gradle uses Kotlin 2.3+.
    jvmToolchain(21)
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.2.1")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.0.0")
    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.2")
}

val coverageIncludes = listOf("io/github/leanish/gradleconventions/**")

tasks.test {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.14"
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
            "license",
            "git-hooks",
            "publishing",
            "maven-publish",
        )

        named("io.github.leanish.java-conventions") {
            displayName = "Leanish Java Conventions"
            description = "Shared Gradle conventions for Java projects."
            tags.set(pluginTags)
        }
    }
}
