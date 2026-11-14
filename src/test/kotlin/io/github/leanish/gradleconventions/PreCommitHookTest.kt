package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
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
}
