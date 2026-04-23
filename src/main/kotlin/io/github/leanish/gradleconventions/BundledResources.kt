/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.nio.charset.StandardCharsets.UTF_8

internal object BundledResources {
    fun load(path: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource(path)) {
            "Missing bundled resource at '$path'"
        }
        return resource.readText(UTF_8)
    }
}
