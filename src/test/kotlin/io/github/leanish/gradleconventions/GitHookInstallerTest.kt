package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class GitHookInstallerTest {
    @TempDir
    lateinit var tempDir: Path

    private val hooks: File get() = tempDir.resolve("hooks").toFile()
    private val hook: File get() = hooks.resolve("pre-commit")

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
}
