/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.io.File

internal object GitHooks {
    fun directory(
        gitMarker: File,
        fallbackHooksDirectory: File,
    ): File {
        if (gitMarker.isDirectory) {
            return gitMarker.resolve("hooks")
        }

        if (!gitMarker.isFile) {
            return fallbackHooksDirectory
        }

        return directoryFromPointer(gitMarker) ?: fallbackHooksDirectory
    }

    fun bundledPreCommitHook(): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("git-hooks/pre-commit")) {
            "Missing bundled pre-commit hook resource"
        }
        return resource.readText()
    }

    private fun directoryFromPointer(gitMarker: File): File? {
        return runCatching {
            val pointerLine = gitMarker.useLines { lines ->
                lines.firstOrNull()
            }?.trim()
            if (pointerLine == null || !pointerLine.startsWith("gitdir:")) {
                return@runCatching null
            }

            val gitDirPath = pointerLine.removePrefix("gitdir:").trim()
            if (gitDirPath.isEmpty()) {
                return@runCatching null
            }

            resolveGitDir(
                gitMarker = gitMarker,
                gitDirPath = gitDirPath,
            ).resolve("hooks")
        }.getOrNull()
    }

    private fun resolveGitDir(
        gitMarker: File,
        gitDirPath: String,
    ): File {
        val gitDir = File(gitDirPath)
        if (gitDir.isAbsolute) {
            return gitDir
        }

        return gitMarker.parentFile.resolve(gitDirPath)
    }
}
