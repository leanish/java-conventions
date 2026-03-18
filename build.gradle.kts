plugins {
    `kotlin-dsl`
    `maven-publish`
    jacoco
    id("com.gradle.plugin-publish") version "2.1.0"
    id("com.diffplug.spotless") version "8.4.0"
}

group = "io.github.leanish"
version = "0.5.3"

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
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.3.0")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
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

publishing {
    repositories {
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
