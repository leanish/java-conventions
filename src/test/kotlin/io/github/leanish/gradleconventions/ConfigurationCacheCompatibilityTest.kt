package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigurationCacheCompatibilityTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun reusesConfigurationCacheWhenEnvironmentOverrideDoesNotChange() {
        val projectDir = tempDir.resolve("cache-env-stable").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)
        writeDumpRepositoriesBuild(projectDir)

        val firstRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpRepositories", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED" to "false",
            ),
        )
        assertThat(firstRun.output)
            .contains("hasMavenCentral=false")
            .contains("Configuration cache entry stored.")

        val secondRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpRepositories", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED" to "false",
            ),
        )
        assertThat(secondRun.output)
            .contains("hasMavenCentral=false")
            .contains("Reusing configuration cache.")
            .contains("Configuration cache entry reused.")
    }

    @Test
    fun invalidatesConfigurationCacheWhenEnvironmentOverrideChanges() {
        val projectDir = tempDir.resolve("cache-env-changed").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)
        writeDumpRepositoriesBuild(projectDir)

        val firstRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpRepositories", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED" to "false",
            ),
        )
        assertThat(firstRun.output)
            .contains("hasMavenCentral=false")
            .contains("Configuration cache entry stored.")

        val secondRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpRepositories", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED" to "true",
            ),
        )
        assertThat(secondRun.output)
            .contains("hasMavenCentral=true")
            .contains("configuration cache cannot be reused")
            .contains("Configuration cache entry stored.")
            .doesNotContain("Reusing configuration cache.")
    }

    @Test
    fun invalidatesConfigurationCacheWhenStringEnvironmentOverrideChanges() {
        val projectDir = tempDir.resolve("cache-owner-changed").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)
        writeDumpPublishingOwnerBuild(projectDir)

        val firstRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpPublishingOwner", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER" to "owner-one",
            ),
        )
        assertThat(firstRun.output)
            .contains("githubPackagesUrl=https://maven.pkg.github.com/owner-one/cache-owner-changed")
            .contains("Configuration cache entry stored.")

        val secondRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("dumpPublishingOwner", "--configuration-cache"),
            environmentOverrides = mapOf(
                "JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER" to "owner-two",
            ),
        )
        assertThat(secondRun.output)
            .contains("githubPackagesUrl=https://maven.pkg.github.com/owner-two/cache-owner-changed")
            .contains("configuration cache cannot be reused")
            .contains("Configuration cache entry stored.")
            .doesNotContain("Reusing configuration cache.")
    }

    @Test
    fun reusesConfigurationCacheWhenGitHooksPathIsResolvedFromGitPointerFile() {
        val projectDir = tempDir.resolve("cache-git-hooks").toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)

        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"cache-git-hooks\"")
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
            ".git",
            "gitdir: .git-worktree",
        )

        val firstRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("installGitHooks", "--configuration-cache"),
            environmentOverrides = emptyMap(),
        )
        assertThat(firstRun.output)
            .contains("Configuration cache entry stored.")

        val generatedHook = projectDir.resolve(".git-worktree/hooks/pre-commit")
        assertThat(generatedHook).exists()

        val secondRun = runGradle(
            projectDir = projectDir,
            arguments = listOf("installGitHooks", "--configuration-cache"),
            environmentOverrides = emptyMap(),
        )
        assertThat(secondRun.output)
            .contains("Reusing configuration cache.")
            .contains("Configuration cache entry reused.")
    }

    private fun runGradle(
        projectDir: File,
        arguments: List<String>,
        environmentOverrides: Map<String, String>,
    ): BuildResult {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(arguments)
            .withEnvironment(
                System.getenv()
                    .toMutableMap()
                    .apply { putAll(environmentOverrides) },
            )
            .withPluginClasspath()
            .build()
    }

    private fun writeDumpRepositoriesBuild(projectDir: File) {
        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"${projectDir.name}\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository

            plugins {
                id("io.github.leanish.java-conventions")
            }

            tasks.register("dumpRepositories") {
                val hasMavenCentral = repositories
                    .withType(MavenArtifactRepository::class.java)
                    .any { repository ->
                        val url = repository.url.toString().removeSuffix("/")
                        url == "https://repo.maven.apache.org/maven2"
                    }
                inputs.property("hasMavenCentral", hasMavenCentral)
                doLast {
                    println("hasMavenCentral=${'$'}{inputs.properties["hasMavenCentral"]}")
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeDumpPublishingOwnerBuild(projectDir: File) {
        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"${projectDir.name}\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.artifacts.repositories.MavenArtifactRepository
            import org.gradle.api.publish.PublishingExtension

            plugins {
                id("io.github.leanish.java-conventions")
            }

            val githubPackagesUrl = (extensions.getByType(PublishingExtension::class.java)
                .repositories
                .findByName("GitHubPackages") as MavenArtifactRepository?)
                ?.url
                ?.toString()
                ?: ""

            tasks.register("dumpPublishingOwner") {
                inputs.property("githubPackagesUrl", githubPackagesUrl)
                doLast {
                    println("githubPackagesUrl=${'$'}{inputs.properties["githubPackagesUrl"]}")
                }
            }
            """.trimIndent(),
        )
    }

}
