package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Path
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

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-defaults\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.tasks.compile.JavaCompile
            import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

            plugins {
                id("io.github.leanish.gradle-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    println("compileJavaPresent=${'$'}{compileJava != null}")
                    println("compileRelease=${'$'}{compileJava?.options?.release?.orNull}")

                    val jacoco = project.tasks.findByName("jacocoTestCoverageVerification") as JacocoCoverageVerification?
                    println("jacocoPresent=${'$'}{jacoco != null}")
                    val minimum = jacoco?.violationRules?.rules?.firstOrNull()?.limits?.firstOrNull()?.minimum
                    println("jacocoMinimum=${'$'}minimum")
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

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-overrides\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.tasks.compile.JavaCompile
            import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

            plugins {
                id("io.github.leanish.gradle-conventions")
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
                    println("compileJavaPresent=${'$'}{compileJava != null}")
                    println("compileRelease=${'$'}{compileJava?.options?.release?.orNull}")

                    val jacoco = project.tasks.findByName("jacocoTestCoverageVerification") as JacocoCoverageVerification?
                    println("jacocoPresent=${'$'}{jacoco != null}")
                    val minimum = jacoco?.violationRules?.rules?.firstOrNull()?.limits?.firstOrNull()?.minimum
                    println("jacocoMinimum=${'$'}minimum")
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
    fun addsLauncherDependencyAndMavenCentralRepository() {
        val projectDir = tempDir.resolve("dependency-behavior").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-dependency-behavior\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import net.ltgt.gradle.errorprone.errorprone
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.tasks.compile.JavaCompile

            plugins {
                id("io.github.leanish.gradle-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val hasMavenCentral = project.repositories
                        .withType(MavenArtifactRepository::class.java)
                        .any { repository ->
                            val url = repository.url.toString().removeSuffix("/")
                            url == "https://repo.maven.apache.org/maven2"
                        }
                    println("hasMavenCentral=${'$'}hasMavenCentral")

                    val runtimeOnlyDependencies = configurations.getByName("testRuntimeOnly").dependencies
                    val launcherDependency = runtimeOnlyDependencies.firstOrNull {
                        it.group == "org.junit.platform" && it.name == "junit-platform-launcher"
                    }
                    println("hasLauncherDependency=${'$'}{launcherDependency != null}")
                    println("launcherVersion=${'$'}{launcherDependency?.version}")

                    val compileJava = project.tasks.findByName("compileJava") as JavaCompile?
                    val nullAwayConfigured = compileJava?.options?.errorprone?.errorproneArgs?.get()?.contains(
                        "-XepOpt:NullAway:AnnotatedPackages=io.github.leanish",
                    ) ?: false
                    println("nullAwayConfigured=${'$'}nullAwayConfigured")
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
            .contains("nullAwayConfigured=true")
    }

    @Test
    fun canDisableMavenCentralWithProperty() {
        val projectDir = tempDir.resolve("repository-toggle").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-repository-toggle\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository

            plugins {
                id("io.github.leanish.gradle-conventions")
            }

            tasks.register("dumpConventions") {
                doLast {
                    val hasMavenCentral = project.repositories
                        .withType(MavenArtifactRepository::class.java)
                        .any { repository ->
                            val url = repository.url.toString().removeSuffix("/")
                            url == "https://repo.maven.apache.org/maven2"
                        }
                    println("hasMavenCentral=${'$'}hasMavenCentral")
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
    fun appliesPublishingConventionsByDefault() {
        val projectDir = tempDir.resolve("publishing-conventions").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-sample\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
            }

            group = "io.github.leanish"
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
            .withPluginClasspath()
            .build()

        val generatedPom = projectDir.resolve("build/publications/mavenJava/pom-default.xml").readText()
        assertThat(generatedPom)
            .contains("<name>publishing-sample</name>")
            .contains("<description>Publishing sample library</description>")
            .contains("<url>https://github.com/leanish/publishing-sample</url>")
            .contains("<connection>scm:git:https://github.com/leanish/publishing-sample.git</connection>")
            .contains(
                "<developerConnection>scm:git:ssh://git@github.com/leanish/publishing-sample.git</developerConnection>",
            )
            .contains("<id>leanish</id>")
            .contains("<name>Leandro Aguiar</name>")
            .contains("<url>https://github.com/leanish</url>")
    }

    @Test
    fun canDisablePublishingConventionsWithProperty() {
        val projectDir = tempDir.resolve("publishing-conventions-disabled").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"publishing-disabled\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
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
    fun appliesLicenseHeaderWhenLicenseHeaderFileExists() {
        val projectDir = tempDir.resolve("license-header").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"license-header\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
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

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"license-header-disabled\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
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
    fun customPreCommitHookIsUsedWhenPresent() {
        val projectDir = tempDir.resolve("hooks").toFile()
        projectDir.mkdirs()

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-hooks\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
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

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"test-bundled-hooks\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.gradle-conventions")
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

    private fun writeFile(projectDir: File, name: String, content: String) {
        val file = projectDir.resolve(name)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun loadBundledPreCommitHook(): String {
        val resource = requireNotNull(PluginResources::class.java.classLoader.getResource("git-hooks/pre-commit")) {
            "Missing bundled pre-commit hook resource"
        }
        return resource.readText()
    }
}
