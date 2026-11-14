package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs the bundled pre-commit hook on real `git commit`s, with a stand-in `gradlew` whose `spotlessApply` "formats" by
 * replacing `UNFORMATTED` with `formatted` and fails on `BROKEN`, and whose Checkstyle always passes.
 */
class PreCommitHookTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: File

    @BeforeEach
    fun setUp() {
        repository = tempDir.resolve("repo").toFile()
        initGitRepository(repository)
        writeFile(
            repository,
            "gradlew",
            """
            #!/usr/bin/env bash
            set -euo pipefail
            if [ "${'$'}1" = spotlessApply ]; then
                if git grep -q BROKEN -- '*.java'; then exit 1; fi
                git ls-files -z -co --exclude-standard -- '*.java' | xargs -0 perl -pi -e 's/UNFORMATTED/formatted/g'
            fi
            """.trimIndent() + "\n",
        )
        repository.resolve("gradlew").setExecutable(true)
        git(repository, "add", "gradlew")
        git(repository, "commit", "-q", "-m", "stand-in gradlew", "--no-verify")
        val hook = repository.resolve(".git/hooks/pre-commit")
        hook.parentFile.mkdirs()
        hook.writeText(GitHooks.bundledPreCommitHook())
        hook.setExecutable(true)
    }

    private fun commit(): Pair<Int, String> {
        val process = ProcessBuilder("git", "commit", "-q", "-m", "change")
            .directory(repository)
            .redirectErrorStream(true)
            .apply {
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_AUTHOR_NAME"] = "Test"
                environment()["GIT_AUTHOR_EMAIL"] = "test@example.com"
                environment()["GIT_COMMITTER_NAME"] = "Test"
                environment()["GIT_COMMITTER_EMAIL"] = "test@example.com"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    /**
     * Runs the installed hook directly, with a stand-in git ahead on the PATH that fails one of its `git diff` calls.
     * (Through `git commit` the stand-in wouldn't be used: Git puts its own directory first on a hook's PATH.)
     */
    private fun runHookWithFailingGitDiff(failingGitDiff: String): Pair<Int, String> {
        val process = ProcessBuilder("bash", ".git/hooks/pre-commit")
            .directory(repository)
            .redirectErrorStream(true)
            .apply {
                environment()["PATH"] = failingGit().parent + File.pathSeparator + environment()["PATH"]
                environment()["FAIL_GIT_DIFF"] = failingGitDiff
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    private fun failingGit(): File {
        val realGit = ProcessBuilder("sh", "-c", "command -v git").start().inputStream.bufferedReader().readText().trim()
        val stub = tempDir.resolve("stub-bin/git").toFile()
        stub.parentFile.mkdirs()
        stub.writeText(
            """
            #!/usr/bin/env bash
            if [ "${'$'}1" = diff ] && [ "${'$'}2" = "${'$'}FAIL_GIT_DIFF" ]; then echo "fatal: injected failure" >&2; exit 128; fi
            exec '$realGit' "${'$'}@"
            """.trimIndent() + "\n",
        )
        stub.setExecutable(true)
        return stub
    }

    private fun stage(name: String, content: String) {
        writeFile(repository, name, content)
        git(repository, "add", "--", name)
    }

    private fun staged(name: String): String = git(repository, "show", ":$name")

    @Test
    fun refusesStagedContentThatFormattingChanges() {
        stage("A.java", "class A { UNFORMATTED }\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("differ from what was checked").contains("A.java")
        assertThat(staged("A.java")).isEqualTo("class A { UNFORMATTED }\n")
    }

    @Test
    fun refusesUnformattedStagedContentBehindAFormattedWorkingCopy() {
        stage("A.java", "class A { UNFORMATTED }\n")
        writeFile(repository, "A.java", "class A { formatted }\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("A.java")
        assertThat(staged("A.java")).isEqualTo("class A { UNFORMATTED }\n")
    }

    @Test
    fun refusesPartiallyStagedFiles() {
        stage("A.java", "class A { formatted }\n")
        writeFile(repository, "A.java", "class A { formatted }\n// not staged\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("A.java")
        assertThat(staged("A.java")).isEqualTo("class A { formatted }\n")
    }

    @Test
    fun acceptsFormattedStagedContent() {
        stage("A.java", "class A { formatted }\n")

        val (exit, output) = commit()

        assertThat(exit).describedAs(output).isZero()
        assertThat(git(repository, "show", "HEAD:A.java")).isEqualTo("class A { formatted }\n")
    }

    @Test
    fun refusesWhenFormattingFails() {
        stage("A.java", "class A { BROKEN }\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("spotlessApply failed")
    }

    @Test
    fun handlesUnusualFileNames() {
        stage("-odd name [1].java", "class Odd { UNFORMATTED }\n")
        stage("Fine.java", "class Fine { formatted }\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("-odd name [1].java").doesNotContain("Fine.java")
        assertThat(staged("-odd name [1].java")).isEqualTo("class Odd { UNFORMATTED }\n")
    }

    @Test
    fun refusesWhenListingTheStagedFilesFails() {
        stage("A.java", "class A { formatted }\n")

        val (exit, output) = runHookWithFailingGitDiff("--cached")

        assertThat(exit).isNotZero()
        assertThat(output).contains("Couldn't list the staged files").doesNotContain("passed!")
    }

    @Test
    fun refusesWhenComparingWithTheWorkingCopyFails() {
        stage("A.java", "class A { formatted }\n")

        val (exit, output) = runHookWithFailingGitDiff("--name-only")

        assertThat(exit).isNotZero()
        assertThat(output).contains("Couldn't compare the staged files").doesNotContain("passed!")
    }

    @Test
    fun refusesAStagedTypeChangeThatDiffersFromTheWorkingCopy() {
        stage("data.txt", "regular\n")
        git(repository, "commit", "-q", "-m", "regular file", "--no-verify")
        val path = repository.resolve("data.txt").toPath()
        Files.delete(path)
        Files.createSymbolicLink(path, Path.of("target"))
        git(repository, "add", "--", "data.txt")
        Files.delete(path)
        writeFile(repository, "data.txt", "regular again\n")

        val (exit, output) = commit()

        assertThat(exit).isNotZero()
        assertThat(output).contains("data.txt")
    }

    @Test
    fun handlesMoreStagedPathsThanFitInOneCommandLine() {
        // About 1.3 MB of paths: more than macOS's 1 MiB argument limit, and passed to git in one go before.
        val directory = "d".repeat(100)
        val names = (1..12_000).map { "$directory/file-${"x".repeat(90)}-$it.txt" }
        names.forEach { writeFile(repository, it, "same\n") }
        git(repository, "add", "--", directory)
        writeFile(repository, names.last(), "changed after staging\n")

        val (exit, output) = commit()

        assertThat(exit).describedAs(output).isNotZero()
        assertThat(output).contains(names.last()).doesNotContain(names.first())
    }
}
