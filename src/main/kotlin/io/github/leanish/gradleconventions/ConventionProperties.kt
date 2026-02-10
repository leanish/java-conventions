/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

internal object ConventionProperties {
    const val MAVEN_CENTRAL_ENABLED_ENV = "JAVA_CONVENTIONS_MAVEN_CENTRAL_ENABLED"
    const val MAVEN_CENTRAL_ENABLED = "leanish.conventions.repositories.mavenCentral.enabled"

    const val PUBLISHING_ENABLED_ENV = "JAVA_CONVENTIONS_PUBLISHING_ENABLED"
    const val PUBLISHING_ENABLED = "leanish.conventions.publishing.enabled"

    const val PUBLISHING_GITHUB_OWNER_ENV = "JAVA_CONVENTIONS_PUBLISHING_GITHUB_OWNER"
    const val GITHUB_REPOSITORY_OWNER_ENV = "GITHUB_REPOSITORY_OWNER" // GitHub-native env var
    const val PUBLISHING_GITHUB_OWNER = "leanish.conventions.publishing.githubOwner"

    const val PUBLISHING_DEVELOPER_ID_ENV = "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_ID"
    const val PUBLISHING_DEVELOPER_ID = "leanish.conventions.publishing.developer.id"

    const val PUBLISHING_DEVELOPER_NAME_ENV = "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_NAME"
    const val PUBLISHING_DEVELOPER_NAME = "leanish.conventions.publishing.developer.name"

    const val PUBLISHING_DEVELOPER_URL_ENV = "JAVA_CONVENTIONS_PUBLISHING_DEVELOPER_URL"
    const val PUBLISHING_DEVELOPER_URL = "leanish.conventions.publishing.developer.url"

    const val BASE_PACKAGE_ENV = "JAVA_CONVENTIONS_BASE_PACKAGE"
    const val BASE_PACKAGE = "leanish.conventions.basePackage"

    const val GITHUB_PACKAGES_USER = "gpr.user"

    const val GITHUB_PACKAGES_KEY = "gpr.key"

    const val GITHUB_ACTOR_ENV = "GITHUB_ACTOR"

    const val GITHUB_TOKEN_ENV = "GITHUB_TOKEN"
}
