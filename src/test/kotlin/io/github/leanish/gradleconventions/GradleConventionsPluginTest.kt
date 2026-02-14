package io.github.leanish.gradleconventions

import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_ID_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_NAME_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_DEVELOPER_URL_ENV
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_OWNER_ENV
import java.nio.file.Path
import java.util.jar.JarFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GradleConventionsPluginTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun defaultsAreApplied() {
        val projectDir = tempDir.resolve("defaults").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-defaults\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.api.tasks.compile.JavaCompile
            import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    println("compileJavaPresent=${compileJava != null}")
                    println("compileRelease=${compileJava?.options?.release?.orNull}")

                    val jacoco = project.tasks.findByName("jacocoTestCoverageVerification") as JacocoCoverageVerification?
                    println("jacocoPresent=${jacoco != null}")
                    val minimum = jacoco?.violationRules?.rules?.firstOrNull()?.limits?.firstOrNull()?.minimum
                    println("jacocoMinimum=$minimum")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("compileJavaPresent=true")
            .contains("jacocoPresent=true")
            .contains("compileRelease=25")
            .contains("jacocoMinimum=0.85")
    }

    @Test
    fun overridesAreApplied() {
        val projectDir = tempDir.resolve("overrides").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-overrides\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.api.tasks.compile.JavaCompile
            import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.withType<JavaCompile>().configureEach {
                options.release.set(17)
            }

            tasks.withType<JacocoCoverageVerification>().configureEach {
                violationRules {
                    rules.forEach { rule ->
                        rule.limits.forEach { limit ->
                            limit.minimum = "0.91".toBigDecimal()
                        }
                    }
                }
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    println("compileJavaPresent=${compileJava != null}")
                    println("compileRelease=${compileJava?.options?.release?.orNull}")

                    val jacoco = project.tasks.findByName("jacocoTestCoverageVerification") as JacocoCoverageVerification?
                    println("jacocoPresent=${jacoco != null}")
                    val minimum = jacoco?.violationRules?.rules?.firstOrNull()?.limits?.firstOrNull()?.minimum
                    println("jacocoMinimum=$minimum")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("compileJavaPresent=true")
            .contains("jacocoPresent=true")
            .contains("compileRelease=17")
            .contains("jacocoMinimum=0.91")
    }

    @Test
    fun excludeTagsSystemPropertyFiltersTaggedTestsAndDisablesCoverageVerification() {
        val projectDir = tempDir.resolve("exclude-tags-filtering").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"exclude-tags-filtering\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpCoverageState") {
                doLast {
                    val verificationTask = tasks
                        .named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java)
                        .get()
                    println("jacocoVerificationEnabled=${verificationTask.enabled}")
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/test/java/io/github/leanish/sample/TagFilteringTest.java",
            """
            package io.github.leanish.sample;

            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            class TagFilteringTest {
                @Test
                void unitTest() {
                }

                @Test
                @Tag("integration")
                void integrationTaggedTest() {
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-DexcludeTags=integration",
                "test",
                "dumpCoverageState",
            )
            .withPluginClasspath()
            .build()

        val testReport = projectDir.resolve("build/test-results/test/TEST-io.github.leanish.sample.TagFilteringTest.xml")
        assertThat(testReport).exists()
        val testReportXml = testReport.readText()

        assertThat(testReportXml)
            .contains("name=\"unitTest()\"")
            .doesNotContain("integrationTaggedTest")
        assertThat(result.output)
            .contains("jacocoVerificationEnabled=false")
    }

    @Test
    fun compileTestJavaDisablesErrorProne() {
        val projectDir = tempDir.resolve("compile-test-java-errorprone").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"compile-test-java-errorprone\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpErrorProneState") {
                doLast {
                    val compileJava = tasks.named("compileJava", JavaCompile::class.java).get()
                    val compileTestJava = tasks.named("compileTestJava", JavaCompile::class.java).get()
                    println("compileJavaErrorProneEnabled=${compileJava.options.errorprone.enabled.get()}")
                    println("compileTestJavaErrorProneEnabled=${compileTestJava.options.errorprone.enabled.get()}")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpErrorProneState")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("compileJavaErrorProneEnabled=true")
            .contains("compileTestJavaErrorProneEnabled=false")
    }

    @Test
    fun addsTestDependenciesAndMavenCentralRepository() {
        val projectDir = tempDir.resolve("dependency-behavior").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-dependency-behavior\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val hasMavenCentral = project.repositories
                        .withType(MavenArtifactRepository::class.java)
                        .any { repository ->
                            val url = repository.url.toString().removeSuffix("/")
                            url == "https://repo.maven.apache.org/maven2"
                        }
                    println("hasMavenCentral=$hasMavenCentral")

                    val runtimeOnlyDependencies = configurations.getByName("testRuntimeOnly").dependencies
                    val launcherDependency = runtimeOnlyDependencies.firstOrNull {
                        it.group == "org.junit.platform" && it.name == "junit-platform-launcher"
                    }
                    println("hasLauncherDependency=${launcherDependency != null}")
                    println("launcherVersion=${launcherDependency?.version}")

                    val testImplementationDependencies = configurations.getByName("testImplementation").dependencies
                    val junitJupiterDependency = testImplementationDependencies.firstOrNull {
                        it.group == "org.junit.jupiter" && it.name == "junit-jupiter"
                    }
                    println("hasJunitJupiterDependency=${junitJupiterDependency != null}")
                    println("junitJupiterVersion=${junitJupiterDependency?.version}")

                    val assertjDependency = testImplementationDependencies.firstOrNull {
                        it.group == "org.assertj" && it.name == "assertj-core"
                    }
                    println("hasAssertjDependency=${assertjDependency != null}")
                    println("assertjVersion=${assertjDependency?.version}")

                    println("hasSourcesJarTask=${project.tasks.findByName("sourcesJar") != null}")
                    println("hasJavadocJarTask=${project.tasks.findByName("javadocJar") != null}")

                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    val nullAwayConfigured = compileJava?.options?.errorprone?.errorproneArgs?.get()?.contains(
                        "-XepOpt:NullAway:AnnotatedPackages=io.github.leanish",
                    ) ?: false
                    println("nullAwayConfigured=$nullAwayConfigured")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("hasMavenCentral=true")
            .contains("hasLauncherDependency=true")
            .contains("launcherVersion=6.0.2")
            .contains("hasJunitJupiterDependency=true")
            .contains("junitJupiterVersion=6.0.2")
            .contains("hasAssertjDependency=true")
            .contains("assertjVersion=3.27.7")
            .contains("hasSourcesJarTask=true")
            .contains("hasJavadocJarTask=true")
            .contains("nullAwayConfigured=true")
    }

    @Test
    fun sourcesAndJavadocsArtifactsCanBeDisabledByConsumer() {
        val projectDir = tempDir.resolve("disable-sources-javadocs").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"disable-sources-javadocs\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.named("sourcesJar") {
                enabled = false
            }

            tasks.named("javadocJar") {
                enabled = false
            }

            tasks.register("dumpConventions") {
                doLast {
                    println("sourcesJarEnabled=${project.tasks.named("sourcesJar").get().enabled}")
                    println("javadocJarEnabled=${project.tasks.named("javadocJar").get().enabled}")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("sourcesJarEnabled=false")
            .contains("javadocJarEnabled=false")
    }

    @Test
    fun canDisableMavenCentralWithProperty() {
        val projectDir = tempDir.resolve("repository-toggle").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-repository-toggle\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val hasMavenCentral = project.repositories
                        .withType(MavenArtifactRepository::class.java)
                        .any { repository ->
                            val url = repository.url.toString().removeSuffix("/")
                            url == "https://repo.maven.apache.org/maven2"
                        }
                    println("hasMavenCentral=$hasMavenCentral")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.repositories.mavenCentral.enabled=false",
                "dumpConventions",
            )
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("hasMavenCentral=false")
    }

    @Test
    fun mavenCentralEnvironmentOverridesProperty() {
        val projectDir = tempDir.resolve("repository-toggle-env").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-repository-toggle-env\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val hasMavenCentral = project.repositories
                        .withType(MavenArtifactRepository::class.java)
                        .any { repository ->
                            val url = repository.url.toString().removeSuffix("/")
                            url == "https://repo.maven.apache.org/maven2"
                        }
                    println("hasMavenCentral=$hasMavenCentral")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.repositories.mavenCentral.enabled=true",
                "dumpConventions",
            )
            .withEnvironment(
                mapOf(
                    "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED" to "false",
                ),
            )
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("hasMavenCentral=false")
    }

    @Test
    fun canOverrideBasePackageWithProperty() {
        val projectDir = tempDir.resolve("base-package-override").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"base-package-override\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    val annotatedPackagesArg = compileJava?.options?.errorprone?.errorproneArgs?.get()?.firstOrNull {
                        it.startsWith("-XepOpt:NullAway:AnnotatedPackages=")
                    }
                    println("annotatedPackagesArg=$annotatedPackagesArg")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.basePackage=com.example",
                "dumpConventions",
            )
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("annotatedPackagesArg=-XepOpt:NullAway:AnnotatedPackages=com.example")
    }

    @Test
    fun basePackageEnvironmentOverridesProperty() {
        val projectDir = tempDir.resolve("base-package-override-env").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            "leanish.conventions.basePackage=com.property.base",
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"base-package-override-env\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    val annotatedPackagesArg = compileJava?.options?.errorprone?.errorproneArgs?.get()?.firstOrNull {
                        it.startsWith("-XepOpt:NullAway:AnnotatedPackages=")
                    }
                    println("annotatedPackagesArg=$annotatedPackagesArg")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withEnvironment(
                mapOf(
                    "JAVA_CONVENTIONS_BASE_PACKAGE" to "com.env.base",
                ),
            )
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("annotatedPackagesArg=-XepOpt:NullAway:AnnotatedPackages=com.env.base")
            .doesNotContain("com.property.base")
    }

    @Test
    fun infersBasePackageFromSourcePackagesWhenPropertyIsMissing() {
        val projectDir = tempDir.resolve("base-package-inferred").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"base-package-inferred\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    val annotatedPackagesArg = compileJava?.options?.errorprone?.errorproneArgs?.get()?.firstOrNull {
                        it.startsWith("-XepOpt:NullAway:AnnotatedPackages=")
                    }
                    val inferredBasePackage = if (project.extensions.extraProperties.has("leanish.conventions.basePackage")) {
                        project.extensions.extraProperties["leanish.conventions.basePackage"]
                    } else {
                        null
                    }
                    println("annotatedPackagesArg=$annotatedPackagesArg")
                    println("inferredBasePackage=$inferredBasePackage")
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/app/Sample.java",
            """
            package com.example.app;

            public class Sample {
                public String value() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/app/internal/Nested.java",
            """
            package com.example.app.internal;

            public class Nested {}
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpConventions")
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("Inferred 'leanish.conventions.basePackage=com.example.app' from source packages under src/main/java")
            .contains("annotatedPackagesArg=-XepOpt:NullAway:AnnotatedPackages=com.example.app")
            .contains("inferredBasePackage=com.example.app")
    }

    @Test
    fun failsWhenBasePackagePropertyIsBlank() {
        val projectDir = tempDir.resolve("base-package-blank").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"base-package-blank\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.basePackage=",
                "tasks",
                "--all",
            )
            .withPluginClasspath()
            .buildAndFail()

        assertThat(result.output)
            .contains("Property 'leanish.conventions.basePackage' must not be blank")
    }

    @Test
    fun failsWhenBasePackagePropertyIsMissing() {
        val projectDir = tempDir.resolve("base-package-missing").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"base-package-missing\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "tasks",
                "--all",
            )
            .withPluginClasspath()
            .buildAndFail()

        assertThat(result.output)
            .contains("Property 'leanish.conventions.basePackage' must be configured")
    }

    @Test
    fun appliesPublishingConventionsByDefault() {
        val projectDir = tempDir.resolve("publishing-conventions").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=acme
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-sample\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.acme"
            version = "1.2.3"
            description = "Publishing sample library"
            """.trimIndent(),
        )

        val tasksResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "tasks",
                "--all",
            )
            .withEnvironment(environmentWithoutGithubRepositoryOwner())
            .withPluginClasspath()
            .build()

        assertThat(tasksResult.output)
            .contains("publishMavenJavaPublicationToMavenLocal")
            .contains("publishMavenJavaPublicationToGitHubPackagesRepository")

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "generatePomFileForMavenJavaPublication",
            )
            .withEnvironment(environmentWithoutGithubRepositoryOwner())
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<name>publishing-sample</name>")
            .contains("<description>Publishing sample library</description>")
            .contains("<url>https://github.com/acme/publishing-sample</url>")
            .contains("<connection>scm:git:https://github.com/acme/publishing-sample.git</connection>")
            .contains(
                "<developerConnection>scm:git:ssh://git@github.com/acme/publishing-sample.git</developerConnection>",
            )
            .contains("<id>acme</id>")
            .contains("<url>https://github.com/acme</url>")
    }

    @Test
    fun publishingGithubOwnerEnvironmentOverridesProperty() {
        val projectDir = tempDir.resolve("publishing-owner-env-precedence").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=property-owner
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-owner-env-precedence\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.group-owner"
            version = "1.0.0"
            description = "publishing owner env precedence"
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForMavenJavaPublication")
            .withEnvironment(mapOf("GITHUB_REPOSITORY_OWNER" to "env-owner"))
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<url>https://github.com/env-owner/publishing-owner-env-precedence</url>")
            .contains("<id>env-owner</id>")
            .doesNotContain("property-owner")
    }

    @Test
    fun publishingOwnerConventionEnvironmentOverridesProperty() {
        val projectDir = tempDir.resolve("publishing-owner-convention-env-precedence").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=property-owner
            """.trimIndent(),
        )

        writeFile(
            projectDir,
            "settings.gradle.kts",
            "rootProject.name = \"publishing-owner-convention-env-precedence\"",
        )
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.group-owner"
            version = "1.0.0"
            description = "publishing owner convention env precedence"
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForMavenJavaPublication")
            .withEnvironment(
                mapOf(
                    "JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER" to "env-owner",
                ),
            )
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<url>https://github.com/env-owner/publishing-owner-convention-env-precedence</url>")
            .contains("<id>env-owner</id>")
            .doesNotContain("property-owner")
    }

    @Test
    fun publishingDeveloperEnvironmentOverridesProperties() {
        val projectDir = tempDir.resolve("publishing-developer-env-precedence").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=acme
            leanish.conventions.publishing.developer.id=property-id
            leanish.conventions.publishing.developer.name=Property Name
            leanish.conventions.publishing.developer.url=https://example.com/property
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-developer-env-precedence\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.acme"
            version = "1.2.3"
            description = "Publishing developer env precedence"
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForMavenJavaPublication")
            .withEnvironment(
                mapOf(
                    "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_ID" to "env-id",
                    "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_NAME" to "Env Name",
                    "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_URL" to "https://example.com/env",
                ),
            )
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<id>env-id</id>")
            .contains("<name>Env Name</name>")
            .contains("<url>https://example.com/env</url>")
            .doesNotContain("property-id")
            .doesNotContain("Property Name")
            .doesNotContain("https://example.com/property")
    }

    @Test
    fun fillsMissingPublishingDeveloperFieldsFromResolvedGithubOwner() {
        val projectDir = tempDir.resolve("publishing-developer-partial").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=acme
            leanish.conventions.publishing.developer.id=partial-id
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-developer-partial\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.acme"
            version = "1.0.0"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "generatePomFileForMavenJavaPublication",
            )
            .withEnvironment(environmentWithoutPublishingOverrides())
            .withPluginClasspath()
            .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<id>partial-id</id>")
            .contains("<name>acme</name>")
            .contains("<url>https://github.com/acme</url>")
    }

    @Test
    fun composesPublishingDeveloperFieldsFromEnvPropertyAndInference() {
        val projectDir = tempDir.resolve("publishing-developer-composed").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=acme
            leanish.conventions.publishing.developer.id=property-id
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-developer-composed\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            group = "io.github.acme"
            version = "1.0.0"
            """.trimIndent(),
        )

        val environment = environmentWithoutPublishingOverrides().toMutableMap().apply {
            put(PUBLISHING_DEVELOPER_NAME_ENV, "Env Name")
        }

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForMavenJavaPublication")
            .withEnvironment(environment)
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<id>property-id</id>")
            .contains("<name>Env Name</name>")
            .contains("<url>https://github.com/acme</url>")
            .doesNotContain("must be configured together")
    }

    @Test
    fun githubPackagesCredentialsEnvironmentOverridesProperties() {
        val projectDir = tempDir.resolve("publishing-credentials-env-precedence").toFile()
        projectDir.mkdirs()
        writeFile(
            projectDir,
            "gradle.properties",
            """
            leanish.conventions.basePackage=io.github.leanish
            leanish.conventions.publishing.githubOwner=acme
            gpr.user=property-user
            gpr.key=property-token
            """.trimIndent(),
        )

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-credentials-env-precedence\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            $$"""
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.credentials.PasswordCredentials
            import org.gradle.api.publish.PublishingExtension

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpGithubPackagesCredentials") {
                doLast {
                    val publishing = project.extensions.getByType(PublishingExtension::class.java)
                    val repository = publishing.repositories.findByName("GitHubPackages") as MavenArtifactRepository?
                    val credentials = repository?.credentials as PasswordCredentials?
                    println("githubPackagesUsername=${credentials?.username}")
                    println("githubPackagesPassword=${credentials?.password}")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("dumpGithubPackagesCredentials")
            .withEnvironment(
                mapOf(
                    "GITHUB_ACTOR" to "env-user",
                    "GITHUB_TOKEN" to "env-token",
                ),
            )
            .withPluginClasspath()
            .build()

        assertThat(result.output)
            .contains("githubPackagesUsername=env-user")
            .contains("githubPackagesPassword=env-token")
            .doesNotContain("property-user")
            .doesNotContain("property-token")
    }

    @Test
    fun canDisablePublishingConventionsWithProperty() {
        val projectDir = tempDir.resolve("publishing-conventions-disabled").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-disabled\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val tasksResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.publishing.enabled=false",
                "tasks",
                "--all",
            )
            .withPluginClasspath()
            .build()

        assertThat(tasksResult.output)
            .doesNotContain("publishMavenJavaPublicationToMavenLocal")
            .doesNotContain("publishMavenJavaPublicationToGitHubPackagesRepository")
    }

    @Test
    fun publishingEnabledEnvironmentOverridesProperty() {
        val projectDir = tempDir.resolve("publishing-conventions-disabled-env").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-disabled-env\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val tasksResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pleanish.conventions.publishing.enabled=true",
                "tasks",
                "--all",
            )
            .withEnvironment(
                mapOf(
                    "JAVA_CONVENTIONS_PUBLISHING_ENABLED" to "false",
                ),
            )
            .withPluginClasspath()
            .build()

        assertThat(tasksResult.output)
            .doesNotContain("publishMavenJavaPublicationToMavenLocal")
            .doesNotContain("publishMavenJavaPublicationToGitHubPackagesRepository")
    }

    @Test
    fun appliesLicenseHeaderWhenLicenseHeaderFileExists() {
        val projectDir = tempDir.resolve("license-header").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"license-header\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "LICENSE_HEADER",
            """
            /*
             * Custom License Header
             */

            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/io/github/leanish/sample/Sample.java",
            """
            package io.github.leanish.sample;

            public class Sample {
                public String value() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("spotlessApply")
            .withPluginClasspath()
            .build()

        val formattedSource =
            projectDir.resolve("src/main/java/io/github/leanish/sample/Sample.java").readText()
        assertThat(formattedSource)
            .contains("Custom License Header")
            .contains("package io.github.leanish.sample;")
    }

    @Test
    fun skipsLicenseHeaderWhenLicenseHeaderFileIsMissing() {
        val projectDir = tempDir.resolve("license-header-disabled").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"license-header-disabled\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/io/github/leanish/sample/Sample.java",
            """
            package io.github.leanish.sample;

            public class Sample {
                public String value() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("spotlessApply")
            .withPluginClasspath()
            .build()

        val formattedSource =
            projectDir.resolve("src/main/java/io/github/leanish/sample/Sample.java").readText()
        assertThat(formattedSource)
            .doesNotContain("Licensed under the MIT License.")
            .contains("package io.github.leanish.sample;")
    }

    @Test
    fun writeCheckstyleConfigUsesProjectFilesWhenPresent() {
        val projectDir = tempDir.resolve("checkstyle-project-files").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"checkstyle-project-files\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val customCheckstyle = "<module name=\"Checker\"><module name=\"TreeWalker\"/></module>"
        val customSuppressions = "<suppressions><suppress files=\".*\"/></suppressions>"
        writeFile(projectDir, "config/checkstyle/checkstyle.xml", customCheckstyle)
        writeFile(projectDir, "config/checkstyle/suppressions.xml", customSuppressions)

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("writeCheckstyleConfig")
            .withPluginClasspath()
            .build()

        val generatedCheckstyle = projectDir.resolve("build/generated/checkstyle/checkstyle.xml").readText()
        val generatedSuppressions = projectDir.resolve("build/generated/checkstyle/suppressions.xml").readText()

        assertThat(generatedCheckstyle).isEqualTo(customCheckstyle)
        assertThat(generatedSuppressions).isEqualTo(customSuppressions)
    }

    @Test
    fun writeCheckstyleConfigFallsBackToBundledFilesWhenProjectFilesAreMissing() {
        val projectDir = tempDir.resolve("checkstyle-bundled-fallback").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"checkstyle-bundled-fallback\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("writeCheckstyleConfig")
            .withPluginClasspath()
            .build()

        val generatedCheckstyle = projectDir.resolve("build/generated/checkstyle/checkstyle.xml").readText()
        val generatedSuppressions = projectDir.resolve("build/generated/checkstyle/suppressions.xml").readText()

        assertThat(generatedCheckstyle).contains("<module name=\"Checker\">")
        assertThat(generatedSuppressions).contains("<suppressions>")
    }

    @Test
    fun generatedCheckstyleFilesAreNotPackagedInJar() {
        val projectDir = tempDir.resolve("checkstyle-not-packaged").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"checkstyle-not-packaged\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "config/checkstyle/checkstyle.xml",
            "<module name=\"Checker\"><module name=\"Regexp\"/></module>",
        )
        writeFile(
            projectDir,
            "config/checkstyle/suppressions.xml",
            "<suppressions><suppress files=\"generated\"/></suppressions>",
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("writeCheckstyleConfig", "jar")
            .withPluginClasspath()
            .build()

        val generatedCheckstyle = projectDir.resolve("build/generated/checkstyle/checkstyle.xml")
        val generatedSuppressions = projectDir.resolve("build/generated/checkstyle/suppressions.xml")
        assertThat(generatedCheckstyle).exists()
        assertThat(generatedSuppressions).exists()

        val jarFiles = projectDir.resolve("build/libs")
            .listFiles { _, name -> name.endsWith(".jar") }
            ?.toList()
            .orEmpty()
        assertThat(jarFiles).hasSize(1)
        val jarFile = jarFiles.single()

        val jarEntries = mutableListOf<String>()
        JarFile(jarFile).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                jarEntries.add(entries.nextElement().name)
            }
        }

        assertThat(jarEntries)
            .doesNotContain("generated/checkstyle/checkstyle.xml")
            .doesNotContain("generated/checkstyle/suppressions.xml")
            .doesNotContain("checkstyle.xml")
            .doesNotContain("suppressions.xml")
    }

    @Test
    fun customPreCommitHookIsUsedWhenPresent() {
        val projectDir = tempDir.resolve("hooks").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-hooks\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val gitHooksDir = projectDir.resolve(".git/hooks")
        gitHooksDir.mkdirs()
        val customHook = projectDir.resolve("scripts/git-hooks/pre-commit")
        customHook.parentFile.mkdirs()
        customHook.writeText("custom-hook")

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("installGitHooks")
            .withPluginClasspath()
            .build()

        val installedHook = gitHooksDir.resolve("pre-commit")
        assertThat(installedHook.readText()).isEqualTo("custom-hook")
    }

    @Test
    fun bundledPreCommitHookIsUsedWhenNoCustomHookIsPresent() {
        val projectDir = tempDir.resolve("bundled-hooks").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-bundled-hooks\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }
            """.trimIndent(),
        )

        val gitHooksDir = projectDir.resolve(".git/hooks")
        gitHooksDir.mkdirs()

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("installGitHooks")
            .withPluginClasspath()
            .build()

        val installedHook = gitHooksDir.resolve("pre-commit")
        assertThat(installedHook.readText())
            .isEqualTo(loadBundledPreCommitHook())
    }

    private fun loadBundledPreCommitHook(): String {
        val resource = requireNotNull(PropertyParser::class.java.classLoader.getResource("git-hooks/pre-commit")) {
            "Missing bundled pre-commit hook resource"
        }
        return resource.readText()
    }

    private fun environmentWithoutGithubRepositoryOwner(): Map<String, String> {
        return System.getenv().toMutableMap().apply {
            remove(GITHUB_REPOSITORY_OWNER_ENV)
        }
    }

    private fun environmentWithoutPublishingOverrides(): Map<String, String> {
        return System.getenv().toMutableMap().apply {
            remove(GITHUB_REPOSITORY_OWNER_ENV)
            remove(PUBLISHING_GITHUB_OWNER_ENV)
            remove(PUBLISHING_DEVELOPER_ID_ENV)
            remove(PUBLISHING_DEVELOPER_NAME_ENV)
            remove(PUBLISHING_DEVELOPER_URL_ENV)
        }
    }
}
