/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_ACTOR_ENV
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV
import io.github.leanish.gradleconventions.ConventionProperties.GITHUB_TOKEN_ENV
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

internal object PropertyParser {
    fun booleanProperty(
        name: String,
        value: String?,
        defaultValue: Boolean,
    ): Boolean {
        val configuredValue = value?.trim() ?: return defaultValue
        return when (configuredValue.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException(
                "Property '$name' must be 'true' or 'false', got '$configuredValue'",
            )
        }
    }

    fun stringProperty(
        name: String,
        value: String?,
    ): String? {
        return nonBlank(value, source = "Property '$name'")
    }

    /** Like [stringProperty], for a value read from the environment variable [envName]; a blank one names the variable. */
    fun environmentStringProperty(
        name: String,
        envName: String,
        value: String?,
    ): String? {
        return nonBlank(value, source = "Environment variable '$envName' (for property '$name')")
    }

    private fun nonBlank(
        value: String?,
        source: String,
    ): String? {
        val configuredValue = value ?: return null
        val trimmedValue = configuredValue.trim()
        if (trimmedValue.isEmpty()) {
            throw GradleException("$source must not be blank")
        }
        return trimmedValue
    }
}

/**
 * Variables that GitHub Actions or the developer's environment sets, not this plugin's own. Set but blank (a secret a
 * workflow run can't read, an `export GITHUB_TOKEN=`), they count as unset, so the next source is used.
 */
private val PLATFORM_ENVIRONMENT_VARIABLES = setOf(
    GITHUB_REPOSITORY_OWNER_ENV,
    GITHUB_ACTOR_ENV,
    GITHUB_TOKEN_ENV,
)

internal fun Project.booleanProperty(
    name: String,
    envName: String? = null,
    defaultValue: Boolean,
): Provider<Boolean> {
    val propertyValue = providers.provider {
        findProperty(name)?.toString()
    }
    val configuredValue = envName?.let { providers.environmentVariable(it).orElse(propertyValue) }
        ?: propertyValue

    return configuredValue
        .map { value ->
            PropertyParser.booleanProperty(
                name = name,
                value = value,
                defaultValue = defaultValue,
            )
        }
        .orElse(defaultValue)
}

/** The first of [envNames] that is set, in order, else the property [name]; blank values fail, except as noted above. */
internal fun Project.stringProperty(
    name: String,
    vararg envNames: String,
): String? {
    for (envName in envNames) {
        val envValue = providers.environmentVariable(envName).orNull ?: continue
        if (envValue.isBlank() && envName in PLATFORM_ENVIRONMENT_VARIABLES) {
            continue
        }
        return PropertyParser.environmentStringProperty(
            name = name,
            envName = envName,
            value = envValue,
        )
    }

    return PropertyParser.stringProperty(
        name = name,
        value = findProperty(name)?.toString(),
    )
}
