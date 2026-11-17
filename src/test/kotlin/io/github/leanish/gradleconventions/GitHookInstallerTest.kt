package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

class GitHookInstallerTest {
    @TempDir
    lateinit var tempDir: Path

    private val hooks: File get() = tempDir.resolve("hooks").toFile()
    private val hook: File get() = hooks.resolve("pre-commit")
    private val record: File get() = hooks.resolve(GitHookInstaller.RECORD_NAME)

    private fun previouslyBundled(commit: String): ByteArray =
        checkNotNull(javaClass.getResource("/previously-bundled-hooks/pre-commit-$commit")).readBytes()

    private fun makeNotExecutable(file: File) {
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
    }

    @Test
    fun installsWhenNoHookExists() {
        val outcome = GitHookInstaller.install("v1".toByteArray(), hooks)

        assertThat(outcome).isEqualTo(HookInstallOutcome.INSTALLED)
        assertThat(hook).hasContent("v1")
        assertThat(hook.canExecute()).isTrue()
        assertThat(hooks.resolve(GitHookInstaller.RECORD_NAME)).exists()
    }

    @Test
    fun copiesTheSourceByteForByte() {
        val source = byteArrayOf(0x23, 0x21, 0x0d, 0x0a, 0x00, 0x7f)

        GitHookInstaller.install(source, hooks)

        assertThat(hook.readBytes()).isEqualTo(source)
    }

    @Test
    fun leavesAnIdenticalHookUnchanged() {
        GitHookInstaller.install("v1".toByteArray(), hooks)

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.UNCHANGED)
        assertThat(hook).hasContent("v1")
    }

    @Test
    fun updatesAHookItInstalledThatWasNotEdited() {
        GitHookInstaller.install("v1".toByteArray(), hooks)

        assertThat(GitHookInstaller.install("v2".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.UPDATED)
        assertThat(hook).hasContent("v2")
    }

    @Test
    fun keepsAHookItDidNotInstall() {
        hooks.mkdirs()
        hook.writeText("another tool's hook")

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_UNMANAGED)
        assertThat(hook).hasContent("another tool's hook")
        assertThat(hooks.resolve(GitHookInstaller.RECORD_NAME)).doesNotExist()
    }

    @Test
    fun keepsAnInstalledHookThatWasEditedLocally() {
        GitHookInstaller.install("v1".toByteArray(), hooks)
        hook.writeText("v1 plus a local line")

        assertThat(GitHookInstaller.install("v2".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_UNMANAGED)
        assertThat(hook).hasContent("v1 plus a local line")
    }

    @Test
    fun keepsASymbolicLink() {
        val target = tempDir.resolve("shared-hook").toFile()
        target.writeText("shared")
        hooks.mkdirs()
        Files.createSymbolicLink(hook.toPath(), target.toPath())

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_SYMLINK)
        assertThat(target).hasContent("shared")
        assertThat(Files.isSymbolicLink(hook.toPath())).isTrue()
    }

    @Test
    fun upgradesEachPreviouslyBundledHookInstalledBeforeTheRecord() {
        for (commit in listOf("689378a", "d511ef3")) {
            hooks.deleteRecursively()
            hooks.mkdirs()
            hook.writeBytes(previouslyBundled(commit))

            assertThat(GitHookInstaller.install("v2".toByteArray(), hooks)).describedAs(commit).isEqualTo(HookInstallOutcome.UPDATED)
            assertThat(hook).describedAs(commit).hasContent("v2")
            assertThat(hook.canExecute()).describedAs(commit).isTrue()
        }
    }

    @Test
    fun keepsAPreviouslyBundledHookWhenTheRecordDisagrees() {
        GitHookInstaller.install("v1".toByteArray(), hooks)
        hook.writeBytes(previouslyBundled("d511ef3"))

        assertThat(GitHookInstaller.install("v2".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_UNMANAGED)
        assertThat(hook.readBytes()).isEqualTo(previouslyBundled("d511ef3"))
    }

    @Test
    fun makesAnIdenticalHookThatIsNotExecutableExecutable() {
        hooks.mkdirs()
        hook.writeText("v1")
        makeNotExecutable(hook)

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.UNCHANGED)
        assertThat(hook.canExecute()).isTrue()
    }

    @Test
    fun gitRunsAnAdoptedHookThatWasNotExecutable() {
        val repository = tempDir.resolve("repo").toFile()
        git(repository.parentFile, "init", "-q", repository.name)
        val marker = tempDir.resolve("hook-ran").toFile()
        val source = "#!/bin/sh\ntouch '${marker.absolutePath}'\n".toByteArray()
        val repositoryHooks = repository.resolve(".git/hooks")
        repositoryHooks.mkdirs()
        repositoryHooks.resolve("pre-commit").writeBytes(source)
        makeNotExecutable(repositoryHooks.resolve("pre-commit"))

        GitHookInstaller.install(source, repositoryHooks)
        git(repository, "commit", "-q", "--allow-empty", "-m", "empty")

        assertThat(marker).exists()
    }

    @Test
    fun doesNotWriteThroughALinkedRecord() {
        val target = tempDir.resolve("elsewhere").toFile()
        target.writeText("keep me")
        hooks.mkdirs()
        Files.createSymbolicLink(record.toPath(), target.toPath())

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_NOT_REGULAR)
        assertThat(target).hasContent("keep me")
        assertThat(hook).doesNotExist()
    }

    @Test
    fun doesNotWriteThroughADanglingRecordLink() {
        val target = tempDir.resolve("missing").toFile()
        hooks.mkdirs()
        Files.createSymbolicLink(record.toPath(), target.toPath())

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_NOT_REGULAR)
        assertThat(target).doesNotExist()
        assertThat(hook).doesNotExist()
    }

    @Test
    fun keepsAHookThatIsADirectory() {
        hook.mkdirs()

        assertThat(GitHookInstaller.install("v1".toByteArray(), hooks)).isEqualTo(HookInstallOutcome.KEPT_NOT_REGULAR)
        assertThat(hook).isDirectory()
        assertThat(record).doesNotExist()
    }

    private fun git(directory: File, vararg arguments: String) {
        val process = ProcessBuilder("git", "-c", "user.name=Test", "-c", "user.email=test@example.com", *arguments)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
    }
}
