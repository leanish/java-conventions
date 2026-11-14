/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
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

    /** Where Git runs hooks for this repository, or why the hook isn't installed. */
    @get:Internal
    abstract val hooksLocation: Property<HooksLocation>

    /**
     * What happened, for `setupProject`: the first line says whether it was already logged at lifecycle level or above
     * (`shown`) or only at info (`quiet`); the rest is the message.
     */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun install() {
        val (level, message) = installAndDescribe()
        logger.log(level, message)
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText((if (level == LogLevel.INFO) "quiet" else "shown") + "\n" + message)
    }

    private fun installAndDescribe(): Pair<LogLevel, String> {
        val hooksDirectory = when (val location = hooksLocation.get()) {
            is HooksLocation.Install -> location.directory
            is HooksLocation.Skip -> {
                val level = if (location.expected) LogLevel.INFO else LogLevel.WARN
                return level to "Didn't install the pre-commit hook: ${location.reason}."
            }
        }
        val hook = hooksDirectory.resolve(GitHookInstaller.HOOK_NAME)
        return when (GitHookInstaller.install(hookFile.get().readBytes(), hooksDirectory)) {
            HookInstallOutcome.INSTALLED -> LogLevel.LIFECYCLE to "Installed the pre-commit hook in $hook"
            HookInstallOutcome.UPDATED -> LogLevel.LIFECYCLE to "Updated the pre-commit hook in $hook"
            HookInstallOutcome.UNCHANGED -> LogLevel.INFO to "The pre-commit hook in $hook is up to date"
            HookInstallOutcome.KEPT_UNMANAGED -> LogLevel.WARN to
                "Left $hook unchanged: java-conventions didn't install it, or it was edited since. " +
                "To use the conventions' hook, delete it and run installGitHooks."
            HookInstallOutcome.KEPT_SYMLINK -> LogLevel.WARN to "Left $hook unchanged: it is a symbolic link."
            HookInstallOutcome.KEPT_NOT_REGULAR -> LogLevel.WARN to
                "Left $hook unchanged: it, or its record ${GitHookInstaller.RECORD_NAME} next to it, is a symbolic link " +
                "or not a regular file."
        }
    }
}
