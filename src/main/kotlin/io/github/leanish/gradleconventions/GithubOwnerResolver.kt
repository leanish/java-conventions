/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

internal object GithubOwnerResolver {
    private const val GITHUB_GROUP_PREFIX = "io.github."

    fun inferFromGroup(groupValue: Any?): String? {
        // Gradle's "unspecified" group doesn't start with the prefix either.
        val groupText = groupValue?.toString()?.trim()
        if (groupText == null || !groupText.startsWith(GITHUB_GROUP_PREFIX)) {
            return null
        }

        return groupText
            .removePrefix(GITHUB_GROUP_PREFIX)
            .substringBefore('.')
            .takeIf(String::isNotBlank)
    }
}
