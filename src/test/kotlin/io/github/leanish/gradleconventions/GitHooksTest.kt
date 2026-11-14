package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Decides where to install from what a real git prints, the way the convention script does. */
class GitHooksTest {
    @TempDir
    lateinit var tempDir: Path

    private fun run(directory: File, command: List<String>, globalConfig: File?): GitCommandResult {
        val process = ProcessBuilder(command)
            .directory(directory)
            .apply {
                environment()["GIT_CONFIG_GLOBAL"] = globalConfig?.path ?: "/dev/null"
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        return GitCommandResult.Finished(process.waitFor(), output, errorOutput)
    }

    private fun locate(projectDirectory: File, globalConfig: File? = null): HooksLocation =
        GitHooks.locate(
            projectDirectory,
            run(projectDirectory, GitHooks.HOOKS_PATH_CONFIG_COMMAND, globalConfig),
            run(projectDirectory, GitHooks.GIT_PATHS_COMMAND, globalConfig),
        )

    private fun installDirectory(location: HooksLocation): File {
        assertThat(location).isInstanceOf(HooksLocation.Install::class.java)
        return (location as HooksLocation.Install).directory.canonicalFile
    }

    @Test
    fun ordinaryRepositoryUsesItsGitDirectory() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)

        assertThat(installDirectory(locate(repository))).isEqualTo(repository.resolve(".git/hooks").canonicalFile)
    }

    @Test
    fun linkedWorktreeUsesTheMainRepositoryHooks() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        val worktree = tempDir.resolve("linked").toFile()
        git(repository, "worktree", "add", "-q", worktree.path)

        assertThat(installDirectory(locate(worktree))).isEqualTo(repository.resolve(".git/hooks").canonicalFile)
    }

    @Test
    fun aRepositoryHooksPathIsLeftAlone() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        git(repository, "config", "core.hooksPath", ".githooks")

        val location = locate(repository)

        assertThat(location).isEqualTo(
            HooksLocation.Skip("core.hooksPath is set (\".githooks\"), so hooks there are left to you", expected = true),
        )
    }

    @Test
    fun aGlobalHooksPathIsLeftAlone() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        val globalConfig = tempDir.resolve("gitconfig").toFile()
        globalConfig.writeText("[core]\n\thooksPath = ${tempDir.resolve("machine-hooks")}\n")

        val location = locate(repository, globalConfig)

        assertThat(location).isInstanceOf(HooksLocation.Skip::class.java)
        assertThat((location as HooksLocation.Skip).expected).isTrue()
    }

    @Test
    fun anEmptyHooksPathIsLeftAlone() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        git(repository, "config", "core.hooksPath", "")

        assertThat(locate(repository)).isEqualTo(
            HooksLocation.Skip("core.hooksPath is set (\"\"), so hooks there are left to you", expected = true),
        )
    }

    @Test
    fun aHooksDirectoryLinkedOutsideTheGitDirectoryIsSkipped() {
        val repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        val outside = tempDir.resolve("outside").toFile().apply { mkdirs() }
        val hooks = repository.resolve(".git/hooks")
        hooks.deleteRecursively()
        Files.createSymbolicLink(hooks.toPath(), outside.toPath())

        val location = locate(repository)

        assertThat(location).isInstanceOf(HooksLocation.Skip::class.java)
        assertThat((location as HooksLocation.Skip).expected).isFalse()
        assertThat(location.reason).contains("outside the repository's git directory")
    }

    @Test
    fun keepsSpacesAtTheEndOfAPath() {
        val output = "/work/repo/.git\n/work/repo/.git/hooks \n"
        val location = GitHooks.locate(
            File("/work/repo"),
            GitCommandResult.Finished(1, "", ""),
            GitCommandResult.Finished(0, output, ""),
        )

        assertThat(location).isEqualTo(HooksLocation.Install(File("/work/repo/.git/hooks ")))
    }

    @Test
    fun gitThatCannotStartSkipsWithTheReason() {
        val location = GitHooks.locate(
            tempDir.toFile(),
            GitCommandResult.NotStarted("A problem occurred starting process 'command 'git''"),
            GitCommandResult.NotStarted("A problem occurred starting process 'command 'git''"),
        )

        assertThat(location).isEqualTo(
            HooksLocation.Skip("couldn't run git: A problem occurred starting process 'command 'git''", expected = false),
        )
    }

    @Test
    fun aFailedLookupSkipsWithGitsMessage() {
        val location = GitHooks.locate(
            tempDir.toFile(),
            GitCommandResult.Finished(1, "", ""),
            GitCommandResult.Finished(128, "", "fatal: detected dubious ownership in repository\n"),
        )

        assertThat(location).isEqualTo(
            HooksLocation.Skip(
                "`git rev-parse --git-common-dir --git-path hooks` exited with 128: fatal: detected dubious ownership in repository",
                expected = false,
            ),
        )
    }
}
