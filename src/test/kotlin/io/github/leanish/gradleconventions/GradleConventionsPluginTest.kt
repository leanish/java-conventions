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
        projectDir.resolve(name).writeText(content)
    }

    private fun loadBundledPreCommitHook(): String {
        val resource = requireNotNull(PluginResources::class.java.classLoader.getResource("git-hooks/pre-commit")) {
            "Missing bundled pre-commit hook resource"
        }
        return resource.readText()
    }
}
