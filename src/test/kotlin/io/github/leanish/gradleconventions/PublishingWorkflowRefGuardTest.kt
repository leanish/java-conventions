package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the reusable publishing workflow's ref guard, the shell block between its `BEGIN ref guard` and `END ref guard`
 * markers, which both the build job and the upload job carry.
 */
class PublishingWorkflowRefGuardTest {
    private val workflow = File(".github/workflows/publishing-github-packages.yml")

    private fun guards(): List<String> {
        val lines = workflow.readLines()
        val begins = lines.indices.filter { lines[it].trim().startsWith("# BEGIN ref guard") }
        return begins.map { begin ->
            val end = (begin until lines.size).first { lines[it].trim() == "# END ref guard" }
            lines.subList(begin, end + 1).joinToString("\n").trimIndent()
        }
    }

    @Test
    fun bothJobsCarryTheSameGuard() {
        val guards = guards()

        assertThat(guards).hasSize(2)
        assertThat(guards[1]).isEqualTo(guards[0])
    }

    @ParameterizedTest(name = "{0} from {1} {2} -> exit {3}")
    @CsvSource(
        "1.2.3, tag, v1.2.3, 0",
        "1.2.3, tag, v1.2.4, 1",
        "1.2.3, branch, main, 1",
        "1.2.3, branch, v1.2.3, 1",
        "1.2.4-SNAPSHOT, branch, main, 0",
        "1.2.4-SNAPSHOT, branch, feature, 0",
        "1.2.4-SNAPSHOT, tag, v1.2.3, 1",
        "1.2.4-SNAPSHOT, tag, v1.2.4, 1",
    )
    fun guardAcceptsOnlyAReleaseFromItsOwnTag(version: String, refType: String, refName: String, expectedExit: Int) {
        val process = ProcessBuilder("bash", "-c", "set -euo pipefail\n" + guards().first())
            .redirectErrorStream(true)
            .apply {
                environment()["VERSION"] = version
                environment()["REF_TYPE"] = refType
                environment()["REF_NAME"] = refName
            }
            .start()

        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
        assertThat(process.exitValue()).isEqualTo(expectedExit)
    }
}
