/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** What running one git command gave. */
internal sealed interface GitCommandResult : Serializable {
    data class Finished(val exitValue: Int, val output: String, val errorOutput: String) : GitCommandResult

    /** Git couldn't be started at all (not on the `PATH`, for example). */
    data class NotStarted(val message: String) : GitCommandResult
}

/** Where `installGitHooks` installs the pre-commit hook, or why it doesn't. */
internal sealed interface HooksLocation : Serializable {
    data class Install(val directory: File) : HooksLocation

    /**
     * Nothing is installed. [expected] is true when that is the developer's choice (they set `core.hooksPath`), false
     * when something went wrong looking the directory up.
     */
    data class Skip(val reason: String, val expected: Boolean) : HooksLocation
}

internal object GitHooks {
    /** Exits 1 when `core.hooksPath` isn't set anywhere (repository, worktree, global or system config). */
    val HOOKS_PATH_CONFIG_COMMAND = listOf("git", "config", "--get", "core.hooksPath")

    /** Prints the repository's common git directory, then the hooks directory Git uses here (linked worktrees included). */
    val GIT_PATHS_COMMAND = listOf("git", "rev-parse", "--git-common-dir", "--git-path", "hooks")

    /**
     * Where to install, from [hooksPathConfig] ([HOOKS_PATH_CONFIG_COMMAND]) and [gitPaths] ([GIT_PATHS_COMMAND]), both
     * run in [projectDirectory]. A configured `core.hooksPath` is left alone: hooks there belong to whoever set it (a
     * global one would apply to every repository on the machine). So is a hooks directory that resolves outside the
     * repository's git directory. A failed lookup skips with the reason instead of failing the build.
     */
    fun locate(projectDirectory: File, hooksPathConfig: GitCommandResult, gitPaths: GitCommandResult): HooksLocation {
        when (hooksPathConfig) {
            is GitCommandResult.NotStarted -> return lookupFailed("couldn't run git: ${hooksPathConfig.message}")
            is GitCommandResult.Finished -> when (hooksPathConfig.exitValue) {
                0 -> return HooksLocation.Skip(
                    "core.hooksPath is set (\"${withoutLineTerminator(hooksPathConfig.output)}\"), so hooks there are left to you",
                    expected = true,
                )
                1 -> Unit
                else -> return lookupFailed(failure(HOOKS_PATH_CONFIG_COMMAND, hooksPathConfig))
            }
        }
        val output = when (gitPaths) {
            is GitCommandResult.NotStarted -> return lookupFailed("couldn't run git: ${gitPaths.message}")
            is GitCommandResult.Finished -> {
                if (gitPaths.exitValue != 0) {
                    return lookupFailed(failure(GIT_PATHS_COMMAND, gitPaths))
                }
                gitPaths.output
            }
        }
        // Only Git's line terminators are removed: a path may start or end with spaces.
        val lines = withoutLineTerminator(output).split("\n")
        if (lines.size != 2 || lines.any(String::isEmpty)) {
            return lookupFailed("`${GIT_PATHS_COMMAND.joinToString(" ")}` printed something unexpected: \"$output\"")
        }
        val (commonDirectory, hooksDirectory) = lines.map { resolve(projectDirectory, it) }
        val hooksPath = hooksDirectory.toPath()
        // A link to a missing directory can't be checked for containment, and installing would fail on it.
        if (Files.isSymbolicLink(hooksPath) && !Files.exists(hooksPath)) {
            return lookupFailed("the hooks directory $hooksDirectory is a link to ${Files.readSymbolicLink(hooksPath)}, which doesn't exist")
        }
        val inside = try {
            realPathOfExistingPart(hooksDirectory).startsWith(realPathOfExistingPart(commonDirectory))
        } catch (exception: IOException) {
            return lookupFailed("couldn't resolve the hooks directory $hooksDirectory: $exception")
        }
        if (!inside) {
            return lookupFailed("the hooks directory $hooksDirectory is outside the repository's git directory $commonDirectory")
        }
        return HooksLocation.Install(hooksDirectory)
    }

    fun bundledPreCommitHook(): String {
        return BundledResources.load("git-hooks/pre-commit")
    }

    private fun lookupFailed(reason: String) = HooksLocation.Skip(reason, expected = false)

    private fun failure(command: List<String>, result: GitCommandResult.Finished): String =
        "`${command.joinToString(" ")}` exited with ${result.exitValue}: ${result.errorOutput.trim()}"

    private fun withoutLineTerminator(output: String): String = output.removeSuffix("\n").removeSuffix("\r")

    /** Git prints paths relative to the directory it ran in unless they are absolute. */
    private fun resolve(projectDirectory: File, path: String): File =
        File(path).let { if (it.isAbsolute) it else projectDirectory.resolve(path) }

    /** The real path (links resolved) of [file]'s nearest existing ancestor, with the rest appended. */
    private fun realPathOfExistingPart(file: File): Path {
        val path = file.toPath().toAbsolutePath().normalize()
        var existing = path
        // NOFOLLOW_LINKS: a dangling link counts as existing, so toRealPath() below fails on it instead of skipping it.
        while (!Files.exists(existing, NOFOLLOW_LINKS)) {
            existing = existing.parent ?: return path
        }
        return existing.toRealPath().resolve(existing.relativize(path))
    }
}
