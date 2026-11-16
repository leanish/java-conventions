package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs the dependency-review workflow's graph target check, the shell block between its `BEGIN graph target check` and
 * `END graph target check` markers, against artifact directories laid out as `actions/download-artifact` extracts them
 * (one directory per artifact). Like the workflow, it needs `bash` and `python3`.
 */
class DependencyGraphWorkflowTest {
    @TempDir
    lateinit var tempDir: Path

    // Gradle runs tests from the project directory.
    private val workflow = File("").absoluteFile.resolve(".github/workflows/dependency-review.yml")

    private val sha = "0123456789abcdef0123456789abcdef01234567"
    private val ref = "refs/pull/7/merge"

    @Test
    fun acceptsGraphsRecordedForThisRun() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(sha, ref))
        writeGraph("dependency-graph_b.json", "dependency-graph-reports/nested/b.json", graph(sha, ref))
        writeGraph("dependency-graph_b.json", "dependency-graph-reports/notes.txt", "not a graph")

        val result = runCheck()

        assertThat(result.exitValue).isZero()
        assertThat(result.output).contains("checked 2 dependency graph file(s)")
    }

    @Test
    fun rejectsAGraphForAnotherCommit() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph("f".repeat(40), ref))

        assertRejected("dependency-graph_a.json/dependency-graph-reports/a.json is recorded for")
    }

    @Test
    fun rejectsAGraphForAnotherRef() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(sha, "refs/heads/main"))

        assertRejected("is recorded for {'sha': '$sha', 'ref': 'refs/heads/main'}")
    }

    @Test
    fun rejectsOneBadGraphAmongGoodOnes() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(sha, ref))
        writeGraph("dependency-graph_b.json", "dependency-graph-reports/deep/er/b.json", graph(sha, "refs/heads/other"))

        assertRejected("dependency-graph_b.json/dependency-graph-reports/deep/er/b.json is recorded for")
    }

    @Test
    fun rejectsAGraphWithoutTheTargetFields() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", """{"sha": "$sha"}""")

        assertRejected("is recorded for {'sha': '$sha', 'ref': None}")
    }

    @Test
    fun rejectsAGraphThatIsNotAnObject() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", """["$sha", "$ref"]""")

        assertRejected("is recorded for None")
    }

    @Test
    fun rejectsMalformedJson() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", """{"sha": "$sha", """)

        assertRejected("dependency-graph_a.json/dependency-graph-reports/a.json isn't JSON")
    }

    @Test
    fun rejectsTheSamePathInTwoArtifacts() {
        // Extracted into one directory, the second would replace the first, so neither is trusted.
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(sha, ref))
        writeGraph("dependency-graph_b.json", "dependency-graph-reports/a.json", graph(sha, ref))

        assertRejected("dependency-graph-reports/a.json is in both dependency-graph_a.json and dependency-graph_b.json")
    }

    @Test
    fun rejectsAnEntryThatIsNotAGraphArtifact() {
        writeGraph("dependency-graph_a.json", "dependency-graph-reports/a.json", graph(sha, ref))
        tempDir.resolve("dependency-graph-check/stray.json").toFile().writeText(graph(sha, ref))

        assertRejected("unexpected entry stray.json")
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

    private fun writeGraph(artifact: String, path: String, content: String) {
        val file = tempDir.resolve("dependency-graph-check").resolve(artifact).resolve(path).toFile()
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun assertRejected(expectedMessage: String) {
        val result = runCheck()

        assertThat(result.exitValue).isEqualTo(1)
        assertThat(result.output).contains("::error::").contains(expectedMessage)
    }

    private class CheckResult(val exitValue: Int, val output: String)

    private fun runCheck(): CheckResult {
        // bash and python3 are required: a missing one fails the test instead of skipping the check.
        val process = ProcessBuilder("bash", "-c", "set -euo pipefail\n" + check())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["RUNNER_TEMP"] = tempDir.toString()
                environment()["EXPECTED_SHA"] = sha
                environment()["EXPECTED_REF"] = ref
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
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
