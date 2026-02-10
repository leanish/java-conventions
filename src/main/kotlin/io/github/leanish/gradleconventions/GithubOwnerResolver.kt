/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.Project

internal object GithubOwnerResolver {
    fun inferFromGroup(groupValue: Any?): String? {
        val groupText = groupValue?.toString()?.trim()
        if (groupText.isNullOrEmpty() || groupText == "unspecified") {
            return null
        }

        val prefix = "io.github."
        if (!groupText.startsWith(prefix)) {
            return null
        }

        val suffix = groupText.removePrefix(prefix)
        if (suffix.isEmpty()) {
            return null
        }

        return suffix.substringBefore('.').takeIf(String::isNotBlank)
    }
}

internal fun Project.githubOwnerFromGroup(): String? {
    return GithubOwnerResolver.inferFromGroup(group)
}
