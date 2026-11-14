package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/** Resolves the hooks directory from what a real git prints, the way the convention script does. */
class GitHooksTest {
    @TempDir
    lateinit var tempDir: Path

    private fun hooksDirectory(projectDirectory: File): File =
        GitHooks.directory(
            projectDirectory = projectDirectory,
            exitValue = 0,
            output = git(projectDirectory, *GitHooks.HOOKS_PATH_COMMAND.drop(1).toTypedArray()),
            errorOutput = "",
        )

    @Test
    fun ordinaryRepositoryUsesItsGitDirectory() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)

        assertThat(hooksDirectory(repository).canonicalFile).isEqualTo(repository.resolve(".git/hooks").canonicalFile)
    }

    @Test
    fun linkedWorktreeUsesTheMainRepositoryHooks() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        val worktree = tempDir.resolve("linked").toFile()
        git(repository, "worktree", "add", "-q", worktree.path)

        assertThat(hooksDirectory(worktree).canonicalFile).isEqualTo(repository.resolve(".git/hooks").canonicalFile)
    }

    @Test
    fun relativeHooksPathIsResolvedAgainstTheProjectDirectory() {
        val repository = tempDir.resolve("repo with spaces").toFile()
        initGitRepository(repository)
        git(repository, "config", "core.hooksPath", ".githooks")

        assertThat(hooksDirectory(repository).canonicalFile).isEqualTo(repository.resolve(".githooks").canonicalFile)
    }

    @Test
    fun absoluteHooksPathIsUsedAsIs() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        val shared = tempDir.resolve("shared-hooks").toFile()
        git(repository, "config", "core.hooksPath", shared.path)

        assertThat(hooksDirectory(repository).canonicalFile).isEqualTo(shared.canonicalFile)
    }

    @Test
    fun failedLookupIsAnError() {
        assertThatThrownBy {
            GitHooks.directory(
                projectDirectory = tempDir.toFile(),
                exitValue = 128,
                output = "",
                errorOutput = "fatal: not a git repository",
            )
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("exited with 128: fatal: not a git repository")
    }
}
