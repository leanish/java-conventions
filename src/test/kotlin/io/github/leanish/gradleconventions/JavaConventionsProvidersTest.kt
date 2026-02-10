package io.github.leanish.gradleconventions

import io.github.leanish.gradleconventions.ConventionProperties.BASE_PACKAGE
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV
import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JavaConventionsProvidersTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun resolvesDefaultsAndInfersBasePackage() {
        val projectDir = tempDir.resolve("defaults").toFile()
        writeFile(
            projectDir,
            "src/main/java/com/example/app/Sample.java",
            """
            package com.example.app;
            public class Sample {}
            """.trimIndent(),
        )
        val project = newJavaProject(projectDir, "defaults")
        project.group = "io.github.acme.lib"

        val providers = project.javaConventionsProviders()
        val githubOwnerFromEnvironment = resolvedGithubOwnerFromEnvironment()

        assertThat(providers.mavenCentralEnabled.get()).isTrue()
        assertThat(providers.publishingConventionsEnabled.get()).isTrue()
        assertThat(providers.publishingGithubOwner.get()).isEqualTo(githubOwnerFromEnvironment ?: "acme")
        assertThat(providers.publishingGithubRepository.get()).isEqualTo("defaults")
        assertThat(providers.publishingPomName.get()).isEqualTo("defaults")
        assertThat(providers.publishingPomDescription.get()).isEqualTo("defaults")
        assertThat(providers.nullAwayAnnotatedPackages.get()).isEqualTo("com.example.app")
        assertThat(project.extensions.extraProperties[BASE_PACKAGE]).isEqualTo("com.example.app")
        assertThat(providers.checkstyleConfigDir.get().asFile.path).endsWith("build/generated/checkstyle")
        assertThat(providers.checkstyleConfigFile.get().path).endsWith("build/generated/checkstyle/checkstyle.xml")
        assertThat(providers.checkstyleSuppressionsFile.get().path).endsWith("build/generated/checkstyle/suppressions.xml")
        assertThat(providers.runtimeLauncher).isNotNull
    }

    @Test
    fun nullAwayAnnotatedPackagesUsesConfiguredProperty() {
        val project = newJavaProject(tempDir.resolve("configured").toFile(), "configured")
        project.extensions.extraProperties.set(BASE_PACKAGE, "com.configured")

        val providers = project.javaConventionsProviders()

        assertThat(providers.nullAwayAnnotatedPackages.get()).isEqualTo("com.configured")
    }

    @Test
    fun nullAwayAnnotatedPackagesFailsWhenPropertyAndSourcesAreMissing() {
        val project = newJavaProject(tempDir.resolve("missing").toFile(), "missing")

        val providers = project.javaConventionsProviders()

        assertThatThrownBy { providers.nullAwayAnnotatedPackages.get() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Property '$BASE_PACKAGE' must be configured")
    }

    @Test
    fun publishingPomDescriptionUsesProjectDescriptionWhenPresent() {
        val project = newJavaProject(tempDir.resolve("description").toFile(), "description")
        project.description = "project-description"

        val providers = project.javaConventionsProviders()

        assertThat(providers.publishingPomDescription.get()).isEqualTo("project-description")
    }

    @Test
    fun publishingGithubOwnerIsEmptyWhenNotConfiguredOrInferable() {
        val project = newJavaProject(tempDir.resolve("owner-missing").toFile(), "owner-missing")

        val providers = project.javaConventionsProviders()

        assertThat(providers.publishingGithubOwner.get()).isEqualTo(resolvedGithubOwnerFromEnvironment() ?: "")
    }

    private fun resolvedGithubOwnerFromEnvironment(): String? {
        return System.getenv(GITHUB_REPOSITORY_OWNER_ENV)?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun newJavaProject(projectDir: File, name: String): Project {
        projectDir.mkdirs()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .withName(name)
            .build()
        project.pluginManager.apply("java")
        project.extensions
            .getByType(JavaPluginExtension::class.java)
            .toolchain
            .languageVersion
            .set(JavaLanguageVersion.of(21))
        return project
    }

    private fun writeFile(projectDir: File, name: String, content: String) {
        val file = projectDir.resolve(name)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
