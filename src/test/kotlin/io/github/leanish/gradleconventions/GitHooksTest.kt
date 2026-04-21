package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitHooksTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun directoryUsesHooksDirectoryInsideGitDirectory() {
        val gitDirectory = tempDir.resolve(".git").toFile()
        val fallbackHooksDirectory = tempDir.resolve("fallback/hooks").toFile()
        gitDirectory.mkdirs()

        assertThat(
            GitHooks.directory(
                gitMarker = gitDirectory,
                fallbackHooksDirectory = fallbackHooksDirectory,
            ),
        ).isEqualTo(gitDirectory.resolve("hooks"))
    }

    @Test
    fun directoryResolvesHooksDirectoryFromRelativeGitPointer() {
        val projectDir = tempDir.resolve("worktree").toFile()
        val fallbackHooksDirectory = tempDir.resolve("fallback/hooks").toFile()
        writeFile(projectDir, ".git", "gitdir: ../actual-git-dir")

        assertThat(
            GitHooks.directory(
                gitMarker = projectDir.resolve(".git"),
                fallbackHooksDirectory = fallbackHooksDirectory,
            ),
        ).isEqualTo(projectDir.resolve("../actual-git-dir/hooks"))
    }

    @Test
    fun directoryFallsBackWhenGitMarkerIsMissing() {
        val fallbackHooksDirectory = tempDir.resolve("fallback/hooks").toFile()
        val missingGitMarker = tempDir.resolve("missing/.git").toFile()

        assertThat(
            GitHooks.directory(
                gitMarker = missingGitMarker,
                fallbackHooksDirectory = fallbackHooksDirectory,
            ),
        ).isEqualTo(fallbackHooksDirectory)
    }

    @Test
    fun directoryFallsBackWhenGitMarkerHasInvalidPointer() {
        val fallbackHooksDirectory = tempDir.resolve("fallback/hooks").toFile()
        val projectDir = tempDir.resolve("invalid").toFile()
        writeFile(projectDir, ".git", "not a git pointer")

        assertThat(
            GitHooks.directory(
                gitMarker = projectDir.resolve(".git"),
                fallbackHooksDirectory = fallbackHooksDirectory,
            ),
        ).isEqualTo(fallbackHooksDirectory)
    }

    @Test
    fun bundledPreCommitHookLoadsBundledResource() {
        assertThat(GitHooks.bundledPreCommitHook())
            .contains("#!/usr/bin/env bash")
            .contains("Running spotlessApply and checkstyle...")
    }
}
