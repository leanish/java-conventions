/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import io.github.leanish.gradleconventions.ConventionProperties.BASE_PACKAGE
import io.github.leanish.gradleconventions.ConventionProperties.BASE_PACKAGE_ENV
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_CENTRAL_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_CENTRAL_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_LOCAL_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.MAVEN_LOCAL_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_ENABLED_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_OWNER
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_OWNER_ENV
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_PACKAGES_ENABLED
import io.github.leanish.gradleconventions.ConventionProperties.PUBLISHING_GITHUB_PACKAGES_ENABLED_ENV
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File

internal data class JavaConventionsProviders(
    val mavenLocalEnabled: Provider<Boolean>,
    val mavenCentralEnabled: Provider<Boolean>,
    val publishingConventionsEnabled: Provider<Boolean>,
    val publishingGithubPackagesEnabled: Provider<Boolean>,
    val publishingGithubOwner: Provider<String>,
    val publishingGithubRepository: Provider<String>,
    val publishingPomName: Provider<String>,
    val publishingPomDescription: Provider<String>,
    val nullAwayAnnotatedPackages: Provider<String>,
    val checkstyleConfigDir: Provider<Directory>,
    val checkstyleConfigFile: Provider<File>,
    val runtimeLauncher: Provider<JavaLauncher>,
)

internal fun Project.javaConventionsProviders(): JavaConventionsProviders {
    val mavenLocalEnabled = booleanProperty(
        name = MAVEN_LOCAL_ENABLED,
        envName = MAVEN_LOCAL_ENABLED_ENV,
        defaultValue = false,
    )
    val mavenCentralEnabled = booleanProperty(
        name = MAVEN_CENTRAL_ENABLED,
        envName = MAVEN_CENTRAL_ENABLED_ENV,
        defaultValue = true,
    )
    val publishingConventionsEnabled = booleanProperty(
        name = PUBLISHING_ENABLED,
        envName = PUBLISHING_ENABLED_ENV,
        defaultValue = true,
    )
    val publishingGithubPackagesEnabled = booleanProperty(
        name = PUBLISHING_GITHUB_PACKAGES_ENABLED,
        envName = PUBLISHING_GITHUB_PACKAGES_ENABLED_ENV,
        defaultValue = true,
    )
    val publishingGithubOwner = providers.provider {
        stringProperty(
            PUBLISHING_GITHUB_OWNER,
            GITHUB_REPOSITORY_OWNER_ENV,
            PUBLISHING_GITHUB_OWNER_ENV,
        )
            ?: githubOwnerFromGroup()
            // Gradle Provider<T> does not support nullable T; blank means "owner unresolved".
            ?: ""
    }
    val publishingGithubRepository = providers.provider { name }
    val publishingPomName = providers.provider { name }
    val publishingPomDescription = providers.provider { description?.takeIf(String::isNotBlank) ?: name }
    val nullAwayAnnotatedPackages = providers.provider {
        resolveNullAwayAnnotatedPackages()
    }
    val checkstyleConfigDir = layout.buildDirectory.dir("generated/checkstyle")
    val checkstyleConfigFile = checkstyleConfigDir.map { it.file("checkstyle.xml").asFile }

    val javaPluginExtension = extensions.getByType(JavaPluginExtension::class.java)
    val javaToolchainService = extensions.getByType(JavaToolchainService::class.java)
    val toolchainSpec = javaPluginExtension.toolchain
    val runtimeLauncher = javaToolchainService.launcherFor {
        languageVersion.set(toolchainSpec.languageVersion)
        vendor.set(toolchainSpec.vendor)
    }

    return JavaConventionsProviders(
        mavenLocalEnabled = mavenLocalEnabled,
        mavenCentralEnabled = mavenCentralEnabled,
        publishingConventionsEnabled = publishingConventionsEnabled,
        publishingGithubPackagesEnabled = publishingGithubPackagesEnabled,
        publishingGithubOwner = publishingGithubOwner,
        publishingGithubRepository = publishingGithubRepository,
        publishingPomName = publishingPomName,
        publishingPomDescription = publishingPomDescription,
        nullAwayAnnotatedPackages = nullAwayAnnotatedPackages,
        checkstyleConfigDir = checkstyleConfigDir,
        checkstyleConfigFile = checkstyleConfigFile,
        runtimeLauncher = runtimeLauncher,
    )
}

private fun Project.resolveNullAwayAnnotatedPackages(): String {
    val configuredBasePackage = stringProperty(
        BASE_PACKAGE,
        BASE_PACKAGE_ENV,
    )
    if (configuredBasePackage != null) {
        return configuredBasePackage
    }

    val detectedBasePackages = detectBasePackages()
    if (detectedBasePackages.isEmpty()) {
        throw GradleException(
            "Property '$BASE_PACKAGE' must be configured or at least one Java package declaration must be discoverable under src/main/java",
        )
    }

    val inferredBasePackages = detectedBasePackages.joinToString(",")
    if (!extensions.extraProperties.has(BASE_PACKAGE)) {
        extensions.extraProperties.set(BASE_PACKAGE, inferredBasePackages)
        logger.lifecycle(
            "Inferred '$BASE_PACKAGE=$inferredBasePackages' from source packages under src/main/java",
        )
    }
    return inferredBasePackages
}
