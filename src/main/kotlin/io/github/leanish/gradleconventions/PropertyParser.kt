/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

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
        val configuredValue = value ?: return null
        val trimmedValue = configuredValue.trim()
        if (trimmedValue.isEmpty()) {
            throw GradleException("Property '$name' must not be blank")
        }
        return trimmedValue
    }
}

internal fun Project.booleanProperty(
    name: String,
    envName: String? = null,
    defaultValue: Boolean,
): Provider<Boolean> {
    return providers.provider {
        val envValue = envName?.let(System::getenv)
        PropertyParser.booleanProperty(
            name = name,
            value = envValue ?: findProperty(name)?.toString(),
            defaultValue = defaultValue,
        )
    }
}

internal fun Project.stringProperty(
    name: String,
    vararg envNames: String,
): String? {
    val envValue = envNames.firstNotNullOfOrNull(System::getenv)
    return PropertyParser.stringProperty(
        name = name,
        value = envValue ?: findProperty(name)?.toString(),
    )
}
