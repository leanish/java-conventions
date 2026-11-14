/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.GradleException
import java.io.File

internal object GitHooks {
    /** The command whose output is the hooks directory Git uses here: linked worktrees and `core.hooksPath` included. */
    val HOOKS_PATH_COMMAND = listOf("git", "rev-parse", "--git-path", "hooks")

    /**
     * The hooks directory from [HOOKS_PATH_COMMAND] run in [projectDirectory]: Git prints it relative to that directory
     * unless it is absolute.
     */
    fun directory(
        projectDirectory: File,
        exitValue: Int,
        output: String,
        errorOutput: String,
    ): File {
        val path = output.trim()
        if (exitValue != 0 || path.isEmpty()) {
            throw GradleException(
                "Couldn't find the git hooks directory: `${HOOKS_PATH_COMMAND.joinToString(" ")}` in $projectDirectory " +
                    "exited with $exitValue: ${errorOutput.trim()}",
            )
        }
        val hooksDirectory = File(path)
        return if (hooksDirectory.isAbsolute) hooksDirectory else projectDirectory.resolve(path)
    }

    fun bundledPreCommitHook(): String {
        return BundledResources.load("git-hooks/pre-commit")
    }
}
