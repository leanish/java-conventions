/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

/**
 * Installs the pre-commit hook with [GitHookInstaller], so a hook the developer owns is kept.
 *
 * Its result depends on the destination's current bytes, which Gradle can't track as an output, so it always runs.
 */
@UntrackedTask(because = "Reads the destination hook to decide whether it may be replaced")
internal abstract class InstallGitHookTask : DefaultTask() {
    /** The hook to install: the project's `scripts/git-hooks/pre-commit`, or the bundled one. */
    @get:Internal
    abstract val hookFile: Property<File>

    /** The repository's hooks directory. */
    @get:Internal
    abstract val hooksDirectory: Property<File>

    @TaskAction
    fun install() {
        val hooksDirectory = hooksDirectory.get()
        val hook = hooksDirectory.resolve(GitHookInstaller.HOOK_NAME)
        when (GitHookInstaller.install(hookFile.get().readBytes(), hooksDirectory)) {
            HookInstallOutcome.INSTALLED -> logger.lifecycle("Installed the pre-commit hook in $hook")
            HookInstallOutcome.UPDATED -> logger.lifecycle("Updated the pre-commit hook in $hook")
            HookInstallOutcome.UNCHANGED -> logger.lifecycle("The pre-commit hook in $hook is up to date")
            HookInstallOutcome.KEPT_UNMANAGED -> logger.warn(
                "Left $hook unchanged: java-conventions didn't install it, or it was edited since. " +
                    "To use the conventions' hook, delete it and run installGitHooks.",
            )
            HookInstallOutcome.KEPT_SYMLINK -> logger.warn("Left $hook unchanged: it is a symbolic link.")
        }
    }
}
