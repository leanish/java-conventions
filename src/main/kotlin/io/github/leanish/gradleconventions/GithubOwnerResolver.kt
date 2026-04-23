/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.Project

internal object GithubOwnerResolver {
    private const val GITHUB_GROUP_PREFIX = "io.github."

    fun inferFromGroup(groupValue: Any?): String? {
        val groupText = groupValue?.toString()?.trim()
        if (groupText.isNullOrEmpty() || groupText == "unspecified") {
            return null
        }

        if (!groupText.startsWith(GITHUB_GROUP_PREFIX)) {
            return null
        }

        return groupText
            .removePrefix(GITHUB_GROUP_PREFIX)
            .substringBefore('.')
            .takeIf(String::isNotBlank)
    }
}

internal fun Project.githubOwnerFromGroup(): String? {
    return GithubOwnerResolver.inferFromGroup(group)
}
