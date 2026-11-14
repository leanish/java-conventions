package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** What `installGitHooks` writes to its report for `setupProject`, per outcome. */
class InstallGitHookTaskTest {
    @TempDir
    lateinit var tempDir: Path

    private val hooks: File get() = tempDir.resolve("hooks").toFile()
    private val report: File get() = tempDir.resolve("report.txt").toFile()

    private fun run(location: HooksLocation, source: String = "v1"): List<String> {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.resolve("project").toFile()).build()
        val hookSource = tempDir.resolve("source").toFile().apply { writeText(source) }
        val task = project.tasks.register("installGitHooks", InstallGitHookTask::class.java) {
            hookFile.set(hookSource)
            hooksLocation.set(location)
            reportFile.set(report)
        }.get()
        task.install()
        return report.readText().split("\n", limit = 2)
    }

    @Test
    fun reportsAnInstallAsShown() {
        assertThat(run(HooksLocation.Install(hooks)))
            .containsExactly("shown", "Installed the pre-commit hook in ${hooks.resolve("pre-commit")}")
    }

    @Test
    fun reportsAnUpdateAsShown() {
        run(HooksLocation.Install(hooks), source = "v1")

        assertThat(run(HooksLocation.Install(hooks), source = "v2"))
            .containsExactly("shown", "Updated the pre-commit hook in ${hooks.resolve("pre-commit")}")
    }

    @Test
    fun reportsAnUpToDateHookAsQuiet() {
        run(HooksLocation.Install(hooks))

        assertThat(run(HooksLocation.Install(hooks)))
            .containsExactly("quiet", "The pre-commit hook in ${hooks.resolve("pre-commit")} is up to date")
    }

    @Test
    fun reportsAKeptHookAsShown() {
        hooks.mkdirs()
        hooks.resolve("pre-commit").writeText("another tool's hook")

        assertThat(run(HooksLocation.Install(hooks))[0]).isEqualTo("shown")
        assertThat(run(HooksLocation.Install(hooks))[1]).startsWith("Left ${hooks.resolve("pre-commit")} unchanged: java-conventions")
    }

    @Test
    fun reportsKeptLinksAndNonRegularFilesAsShown() {
        hooks.mkdirs()
        Files.createSymbolicLink(hooks.resolve("pre-commit").toPath(), tempDir.resolve("elsewhere"))
        assertThat(run(HooksLocation.Install(hooks))).containsExactly("shown", "Left ${hooks.resolve("pre-commit")} unchanged: it is a symbolic link.")

        hooks.resolve("pre-commit").delete()
        hooks.resolve("pre-commit").mkdirs()
        assertThat(run(HooksLocation.Install(hooks))[1]).contains("is a symbolic link or not a regular file")
    }

    @Test
    fun reportsAnExpectedSkipAsQuietAndAFailedLookupAsShown() {
        assertThat(run(HooksLocation.Skip("core.hooksPath is set", expected = true)))
            .containsExactly("quiet", "Didn't install the pre-commit hook: core.hooksPath is set.")
        assertThat(run(HooksLocation.Skip("couldn't run git", expected = false)))
            .containsExactly("shown", "Didn't install the pre-commit hook: couldn't run git.")
    }
}
