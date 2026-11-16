package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs the dependency-review workflow's graph target check, the shell block between its `BEGIN graph target check` and
 * `END graph target check` markers, against the layouts `actions/download-artifact` (v8.0.2) produces: a single
 * matching artifact is extracted straight into the target directory, several go one directory per artifact. Like the
 * workflow, it needs `bash` and `python3`.
 */
class DependencyGraphWorkflowTest {
    @TempDir
    lateinit var tempDir: Path

    // Gradle runs tests from the project directory.
    private val workflow = File("").absoluteFile.resolve(".github/workflows/dependency-review.yml")

    private val headSha = "0123456789abcdef0123456789abcdef01234567"
    private val mergeSha = "89abcdef0123456789abcdef0123456789abcdef"
    private val ref = "refs/pull/7/merge"

    @Test
    fun acceptsASingleArtifactExtractedDirectly() {
        writeSingleArtifact("dependency-graph-reports/dependency-review-dependency-graph.json", graph(headSha, ref))

        val result = runCheck(expectedSha = headSha)

        assertThat(result.exitValue).isZero()
        assertThat(result.output).contains("checked 1 dependency graph file(s)")
    }

    @Test
    fun acceptsSeveralArtifactsWithNestedPathsAndOtherFiles() {
        writeArtifact("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(headSha, ref))
        writeArtifact("dependency-graph_b.json", "dependency-graph-reports/nested/b.json", graph(headSha, ref))
        writeArtifact("dependency-graph_b.json", "dependency-graph-reports/notes.txt", "not a graph")

        val result = runCheck(expectedSha = headSha)

        assertThat(result.exitValue).isZero()
        assertThat(result.output).contains("checked 2 dependency graph file(s)")
    }

    /**
     * On a pull request, the pinned `gradle/actions/dependency-submission` (v6.4.0) exports
     * `GITHUB_DEPENDENCY_GRAPH_SHA` as `pull_request.head.sha` (`getShaFromContext` in its `dependency-graph.ts`, the
     * same in the shipped `dist`), and github-dependency-graph-gradle-plugin 1.5.0 writes that value as the snapshot's
     * `sha`. `github.sha` is the merge commit, a different one: a graph recorded for it is rejected.
     */
    @Test
    fun pullRequestGraphsAreRecordedForTheHeadCommitNotTheMergeCommit() {
        assertThat(workflow.readLines().map(String::trim))
            .contains("EXPECTED_SHA: \${{ github.event.pull_request.head.sha || github.sha }}")
            .contains("EXPECTED_REF: \${{ github.ref }}")
        writeSingleArtifact("dependency-graph-reports/a.json", graph(headSha, ref))
        assertThat(runCheck(expectedSha = headSha).exitValue).isZero()

        writeSingleArtifact("dependency-graph-reports/a.json", graph(mergeSha, ref))
        val mergeCommitGraph = runCheck(expectedSha = headSha)

        assertThat(mergeCommitGraph.exitValue).isEqualTo(1)
        assertThat(mergeCommitGraph.output).contains("is recorded for {'sha': '$mergeSha'")
    }

    @Test
    fun rejectsAGraphForAnotherRef() {
        writeSingleArtifact("dependency-graph-reports/a.json", graph(headSha, "refs/heads/main"))

        assertRejected("dependency-graph-reports/a.json is recorded for {'sha': '$headSha', 'ref': 'refs/heads/main'}")
    }

    @Test
    fun rejectsOneBadGraphAmongGoodOnes() {
        writeArtifact("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(headSha, ref))
        writeArtifact("dependency-graph_b.json", "dependency-graph-reports/deep/er/b.json", graph(headSha, "refs/heads/other"))

        assertRejected("dependency-graph_b.json/dependency-graph-reports/deep/er/b.json is recorded for")
    }

    @Test
    fun rejectsAGraphWithoutTheTargetFields() {
        writeSingleArtifact("dependency-graph-reports/a.json", """{"sha": "$headSha"}""")

        assertRejected("is recorded for {'sha': '$headSha', 'ref': None}")
    }

    @Test
    fun rejectsAGraphThatIsNotAnObject() {
        writeSingleArtifact("dependency-graph-reports/a.json", """["$headSha", "$ref"]""")

        assertRejected("is recorded for None")
    }

    @Test
    fun rejectsMalformedJson() {
        writeSingleArtifact("dependency-graph-reports/a.json", """{"sha": "$headSha", """)

        assertRejected("dependency-graph-reports/a.json isn't JSON")
    }

    @Test
    fun rejectsTheSamePathInTwoArtifacts() {
        // The submit step extracts every artifact into one directory, where the second would replace the first.
        writeArtifact("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(headSha, ref))
        writeArtifact("dependency-graph_b.json", "dependency-graph-reports/a.json", graph(headSha, ref))

        assertRejected("dependency-graph-reports/a.json is in both dependency-graph_a.json and dependency-graph_b.json")
    }

    @Test
    fun checksEveryFileWhenTheLayoutIsNotOneDirectoryPerArtifact() {
        writeArtifact("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(headSha, ref))
        writeSingleArtifact("stray.json", graph(headSha, "refs/heads/other"))

        assertRejected("stray.json is recorded for")
    }

    @Test
    fun rejectsNoGraphAtAll() {
        assertRejected("no dependency graph to check")
    }

    @Test
    fun aFailedCheckStopsTheSubmission() {
        val steps = submitJobSteps()

        // Steps stop at the first failure unless a later one opts out with an `if:` or the failing one is allowed to
        // fail; neither may happen here.
        assertThat(steps.map { it.name }).containsExactly(
            "Download the dependency graph",
            "Check the dependency graph's commit and ref",
            "Submit the dependency graph",
        )
        assertThat(steps).allSatisfy { step ->
            assertThat(step.lines).noneMatch { it.trim().startsWith("if:") || it.trim().startsWith("continue-on-error:") }
        }
    }

    private class Step(val name: String, val lines: List<String>)

    /** The `submit` job's steps, read from the workflow text: each step's own lines, without nested script text. */
    private fun submitJobSteps(): List<Step> {
        val lines = workflow.readLines()
        val jobStart = lines.indexOf("  submit:")
        val jobEnd = (jobStart + 1 until lines.size).firstOrNull { lines[it].matches(Regex("^  \\S.*")) } ?: lines.size
        val stepStarts = (jobStart until jobEnd).filter { lines[it].startsWith("      - name: ") }
        return stepStarts.mapIndexed { index, start ->
            val end = stepStarts.getOrElse(index + 1) { jobEnd }
            // Keys of the step itself are indented 8 spaces; deeper lines belong to `with`, `env` or `run`.
            val ownLines = lines.subList(start + 1, end).filter { it.matches(Regex("^ {8}\\S.*")) }
            Step(lines[start].removePrefix("      - name: "), ownLines)
        }
    }

    private fun graph(sha: String, ref: String): String =
        """{"version": 0, "job": {"correlator": "c", "id": "1"}, "sha": "$sha", "ref": "$ref", "manifests": {}}"""

    /** A file of one artifact among several: download-artifact puts each in a directory named after the artifact. */
    private fun writeArtifact(artifact: String, path: String, content: String) {
        writeSingleArtifact("$artifact/$path", content)
    }

    /** A file of the only artifact: download-artifact extracts it straight into the target directory. */
    private fun writeSingleArtifact(path: String, content: String) {
        val file = tempDir.resolve("dependency-graph-check").resolve(path).toFile()
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun assertRejected(expectedMessage: String) {
        val result = runCheck(expectedSha = headSha)

        assertThat(result.exitValue).isEqualTo(1)
        assertThat(result.output).contains("::error::").contains(expectedMessage)
    }

    private class CheckResult(val exitValue: Int, val output: String)

    private fun runCheck(expectedSha: String): CheckResult {
        // bash and python3 are required: a missing one fails the test instead of skipping the check.
        val outputFile = tempDir.resolve("check-output.txt").toFile()
        val process = ProcessBuilder("bash", "-c", "set -euo pipefail\n" + check())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .apply {
                environment()["RUNNER_TEMP"] = tempDir.toString()
                environment()["EXPECTED_SHA"] = expectedSha
                environment()["EXPECTED_REF"] = ref
            }
            .start()
        // The output goes to a file, so a process that never finishes can't block before the bounded wait.
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        val output = outputFile.readText()
        assertThat(finished).describedAs("the check finished within 30 seconds; output: %s", output).isTrue()
        assertThat(output).describedAs("python3 must be on the PATH").doesNotContain("python3: command not found")
        return CheckResult(process.exitValue(), output)
    }

    private fun check(): String {
        val lines = workflow.readLines()
        val begin = lines.indexOfFirst { it.trim().startsWith("# BEGIN graph target check") }
        assertThat(begin).describedAs("BEGIN graph target check marker").isNotNegative()
        val end = (begin until lines.size).first { lines[it].trim() == "# END graph target check" }
        return lines.subList(begin, end + 1).joinToString("\n").trimIndent()
    }
}
