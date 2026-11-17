package io.github.leanish.gradleconventions

import io.github.leanish.gradleconventions.ConventionProperties.BASE_PACKAGE
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_CENTRAL_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_CENTRAL_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_LOCAL_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_LOCAL_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PITEST_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.PITEST_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_PACKAGES_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_PACKAGES_ENABLED_ENV
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

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
        val mavenLocalEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = MAVEN_LOCAL_ENABLED,
            envName = MAVEN_LOCAL_ENABLED_ENV,
            defaultValue = false,
        )
        val mavenCentralEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = MAVEN_CENTRAL_ENABLED,
            envName = MAVEN_CENTRAL_ENABLED_ENV,
            defaultValue = true,
        )
        val publishingConventionsEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = PUBLISHING_ENABLED,
            envName = PUBLISHING_ENABLED_ENV,
            defaultValue = true,
        )
        val publishingGithubPackagesEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = PUBLISHING_GITHUB_PACKAGES_ENABLED,
            envName = PUBLISHING_GITHUB_PACKAGES_ENABLED_ENV,
            defaultValue = true,
        )
        val pitestEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = PITEST_ENABLED,
            envName = PITEST_ENABLED_ENV,
            defaultValue = true,
        )
        val githubOwnerFromEnvironment = resolvedGithubOwnerFromEnvironment()

        assertThat(providers.mavenLocalEnabled.get()).isEqualTo(mavenLocalEnabledFromEnvironment)
        assertThat(providers.mavenCentralEnabled.get()).isEqualTo(mavenCentralEnabledFromEnvironment)
        assertThat(providers.publishingConventionsEnabled.get()).isEqualTo(publishingConventionsEnabledFromEnvironment)
        assertThat(providers.publishingGithubPackagesEnabled.get()).isEqualTo(publishingGithubPackagesEnabledFromEnvironment)
        assertThat(providers.publishingGithubOwner.get()).isEqualTo(githubOwnerFromEnvironment ?: "acme")
        assertThat(providers.publishingGithubRepository.get()).isEqualTo("defaults")
        assertThat(providers.publishingPomName.get()).isEqualTo("defaults")
        assertThat(providers.publishingPomDescription.get()).isEqualTo("defaults")
        assertThat(providers.nullAwayAnnotatedPackages.get()).isEqualTo("com.example.app")
        assertThat(providers.pitestEnabled.get()).isEqualTo(pitestEnabledFromEnvironment)
        assertThat(providers.pitestTargetClasses.get()).containsExactly("com.example.app.*")
        assertThat(project.extensions.extraProperties[BASE_PACKAGE]).isEqualTo("com.example.app")
        assertThat(providers.checkstyleConfigDir.get().asFile.path).endsWith("build/generated/checkstyle")
        assertThat(providers.checkstyleConfigFile.get().path).endsWith("build/generated/checkstyle/checkstyle.xml")
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
    fun pitestTargetClassesCoverEveryConfiguredBasePackage() {
        val project = newJavaProject(tempDir.resolve("pitest-targets").toFile(), "pitest-targets")
        project.extensions.extraProperties.set(BASE_PACKAGE, "com.first, com.second,")

        val providers = project.javaConventionsProviders()

        assertThat(providers.pitestTargetClasses.get()).containsExactly("com.first.*", "com.second.*")
    }

    @Test
    fun subprojectDoesNotTakeTheParentsInferredBasePackage() {
        val root = newJavaProject(tempDir.resolve("root").toFile(), "root")
        writeFile(root.projectDir, "src/main/java/com/acme/app/App.java", "package com.acme.app;\npublic class App {}")
        val lib = newJavaProject(tempDir.resolve("root/lib").toFile(), "lib", parent = root)
        writeFile(lib.projectDir, "src/main/java/com/acme/lib/Lib.java", "package com.acme.lib;\npublic class Lib {}")

        assertThat(root.javaConventionsProviders().nullAwayAnnotatedPackages.get()).isEqualTo("com.acme.app")
        val libProviders = lib.javaConventionsProviders()

        assertThat(libProviders.nullAwayAnnotatedPackages.get()).isEqualTo("com.acme.lib")
        assertThat(libProviders.pitestTargetClasses.get()).containsExactly("com.acme.lib.*")
        assertThat(lib.extensions.extraProperties[BASE_PACKAGE]).isEqualTo("com.acme.lib")
        assertThat(root.extensions.extraProperties[BASE_PACKAGE]).isEqualTo("com.acme.app")
    }

    @Test
    fun subprojectUsesABasePackageConfiguredInTheParent() {
        val root = newJavaProject(tempDir.resolve("configured-root").toFile(), "configured-root")
        root.extensions.extraProperties.set(BASE_PACKAGE, "com.acme")
        val lib = newJavaProject(tempDir.resolve("configured-root/lib").toFile(), "lib", parent = root)
        writeFile(lib.projectDir, "src/main/java/com/acme/lib/Lib.java", "package com.acme.lib;\npublic class Lib {}")

        assertThat(root.javaConventionsProviders().nullAwayAnnotatedPackages.get()).isEqualTo("com.acme")
        assertThat(lib.javaConventionsProviders().nullAwayAnnotatedPackages.get()).isEqualTo("com.acme")
    }

    @Test
    fun subprojectUsesAValueSetOverTheParentsInferredOne() {
        val root = newJavaProject(tempDir.resolve("overwritten-root").toFile(), "overwritten-root")
        writeFile(root.projectDir, "src/main/java/com/acme/app/App.java", "package com.acme.app;\npublic class App {}")
        val lib = newJavaProject(tempDir.resolve("overwritten-root/lib").toFile(), "lib", parent = root)
        writeFile(lib.projectDir, "src/main/java/com/acme/lib/Lib.java", "package com.acme.lib;\npublic class Lib {}")

        assertThat(root.javaConventionsProviders().nullAwayAnnotatedPackages.get()).isEqualTo("com.acme.app")
        // A build script sets the root's value after the root inferred one: that is configuration again.
        root.extensions.extraProperties.set(BASE_PACKAGE, "com.acme")

        assertThat(lib.javaConventionsProviders().nullAwayAnnotatedPackages.get()).isEqualTo("com.acme")
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

    @Test
    fun publishingGithubPackagesEnabledCanBeDisabledWithProperty() {
        val project = newJavaProject(tempDir.resolve("github-packages-disabled").toFile(), "github-packages-disabled")
        project.extensions.extraProperties.set("leanish.conventions.publishing.githubPackages.enabled", "false")

        val providers = project.javaConventionsProviders()
        val publishingGithubPackagesEnabledFromPropertyAndEnvironment = expectedBooleanWithPropertyFallback(
            name = PUBLISHING_GITHUB_PACKAGES_ENABLED,
            envName = PUBLISHING_GITHUB_PACKAGES_ENABLED_ENV,
            propertyValue = "false",
            defaultValue = true,
        )
        val publishingConventionsEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = PUBLISHING_ENABLED,
            envName = PUBLISHING_ENABLED_ENV,
            defaultValue = true,
        )

        assertThat(providers.publishingGithubPackagesEnabled.get())
            .isEqualTo(publishingGithubPackagesEnabledFromPropertyAndEnvironment)
        assertThat(providers.publishingConventionsEnabled.get())
            .isEqualTo(publishingConventionsEnabledFromEnvironment)
    }

    @Test
    fun pitestEnabledCanBeDisabledWithProperty() {
        val project = newJavaProject(tempDir.resolve("pitest-disabled").toFile(), "pitest-disabled")
        project.extensions.extraProperties.set("leanish.conventions.pitest.enabled", "false")

        val providers = project.javaConventionsProviders()
        val pitestEnabledFromPropertyAndEnvironment = expectedBooleanWithPropertyFallback(
            name = PITEST_ENABLED,
            envName = PITEST_ENABLED_ENV,
            propertyValue = "false",
            defaultValue = true,
        )

        assertThat(providers.pitestEnabled.get()).isEqualTo(pitestEnabledFromPropertyAndEnvironment)
    }

    @Test
    fun mavenLocalEnabledCanBeEnabledWithProperty() {
        val project = newJavaProject(tempDir.resolve("maven-local-enabled").toFile(), "maven-local-enabled")
        project.extensions.extraProperties.set("leanish.conventions.repositories.mavenLocal.enabled", "true")

        val providers = project.javaConventionsProviders()
        val mavenLocalEnabledFromPropertyAndEnvironment = expectedBooleanWithPropertyFallback(
            name = MAVEN_LOCAL_ENABLED,
            envName = MAVEN_LOCAL_ENABLED_ENV,
            propertyValue = "true",
            defaultValue = false,
        )
        val mavenCentralEnabledFromEnvironment = expectedBooleanFromEnvironment(
            name = MAVEN_CENTRAL_ENABLED,
            envName = MAVEN_CENTRAL_ENABLED_ENV,
            defaultValue = true,
        )

        assertThat(providers.mavenLocalEnabled.get()).isEqualTo(mavenLocalEnabledFromPropertyAndEnvironment)
        assertThat(providers.mavenCentralEnabled.get()).isEqualTo(mavenCentralEnabledFromEnvironment)
    }

    private fun resolvedGithubOwnerFromEnvironment(): String? {
        return System.getenv(GITHUB_REPOSITORY_OWNER_ENV)?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun expectedBooleanFromEnvironment(
        name: String,
        envName: String,
        defaultValue: Boolean,
    ): Boolean {
        val envValue = System.getenv(envName)
        return PropertyParser.booleanProperty(name, envValue, defaultValue)
    }

    private fun expectedBooleanWithPropertyFallback(
        name: String,
        envName: String,
        propertyValue: String,
        defaultValue: Boolean,
    ): Boolean {
        val envValue = System.getenv(envName) ?: propertyValue
        return PropertyParser.booleanProperty(name, envValue, defaultValue)
    }

    private fun newJavaProject(projectDir: File, name: String, parent: Project? = null): Project {
        projectDir.mkdirs()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .withName(name)
            .withParent(parent)
            .build()
        project.pluginManager.apply("java")
        project.extensions
            .getByType(JavaPluginExtension::class.java)
            .toolchain
            .languageVersion
            .set(JavaLanguageVersion.of(21))
        return project
    }
}
