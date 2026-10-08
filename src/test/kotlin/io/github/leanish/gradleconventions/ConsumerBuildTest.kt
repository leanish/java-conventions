package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Runs real builds of a small consumer project, so the tool versions the conventions inject (Error Prone, NullAway,
 * Lombok, JSpecify, Checkstyle, JaCoCo, JUnit, AssertJ) are exercised together rather than only configured.
 */
class ConsumerBuildTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun sampleConsumerBuildPasses() {
        val projectDir = sampleConsumer("consumer-build")

        val result = runner(projectDir, "build").build()

        assertThat(
            listOf(
                ":compileJava",
                ":compileTestJava",
                ":checkstyleMain",
                ":checkstyleTest",
                ":spotlessCheck",
                ":test",
                ":jacocoTestCoverageVerification",
                ":jar",
                ":sourcesJar",
                ":javadocJar",
            ).associateWith { result.task(it)?.outcome },
        ).allSatisfy { _, outcome -> assertThat(outcome).isEqualTo(TaskOutcome.SUCCESS) }
        assertThat(projectDir.resolve("build/test-results/test/TEST-io.github.leanish.sample.GreetingTest.xml").readText())
            .contains("tests=\"2\"")
            .contains("failures=\"0\"")
            .contains("errors=\"0\"")
        assertThat(projectDir.resolve("build/libs/consumer-build-1.0.0.jar")).exists()
        assertThat(projectDir.resolve("build/libs/consumer-build-1.0.0-sources.jar")).exists()
        assertThat(projectDir.resolve("build/libs/consumer-build-1.0.0-javadoc.jar")).exists()
    }

    @Test
    fun nullAwayFailsCompilationOnNullabilityViolation() {
        val projectDir = sampleConsumer("consumer-nullaway")
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Lookup.java",
            """
            package io.github.leanish.sample;

            public final class Lookup {
                public String find() {
                    return null;
                }
            }
            """.trimIndent(),
        )

        val result = runner(projectDir, "compileJava").buildAndFail()

        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[NullAway] returning @Nullable expression from method with @NonNull return type")
    }

    @Test
    fun jacocoVerificationFailsBelowMinimum() {
        val projectDir = sampleConsumer("consumer-coverage")
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Untested.java",
            """
            package io.github.leanish.sample;

            public final class Untested {
                public int sum(int first, int second) {
                    int total = first;
                    for (int i = 0; i < second; i++) {
                        total++;
                    }
                    return total;
                }

                public String describe(int value) {
                    if (value > 0) {
                        return "positive " + value;
                    }
                    if (value < 0) {
                        return "negative " + value;
                    }
                    return "zero";
                }
            }
            """.trimIndent(),
        )

        val result = runner(projectDir, "check").buildAndFail()

        assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":jacocoTestCoverageVerification")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Rule violated for bundle consumer-coverage: instructions covered ratio is")
    }

    @Test
    fun checkstyleFailsOnViolation() {
        val projectDir = sampleConsumer("consumer-checkstyle")
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Names.java",
            """
            package io.github.leanish.sample;

            import java.util.*;

            public final class Names {
                public List<String> all() {
                    return List.of("Ada");
                }
            }
            """.trimIndent(),
        )

        val result = runner(projectDir, "checkstyleMain").buildAndFail()

        assertThat(result.task(":checkstyleMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[AvoidStarImport]")
    }

    private fun runner(projectDir: File, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .withPluginClasspath()

    // Spotless requires a trailing newline, which trimIndent() drops.
    private fun writeSource(projectDir: File, name: String, content: String) {
        writeFile(projectDir, name, content + "\n")
    }

    private fun sampleConsumer(name: String): File {
        val projectDir = tempDir.resolve(name).toFile()
        projectDir.mkdirs()
        writeRequiredConventionsProperties(projectDir)
        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"$name\"")
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            plugins {
                id("io.github.leanish.java-conventions")
            }

            version = "1.0.0"
            """.trimIndent(),
        )
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/package-info.java",
            """
            @NullMarked
            package io.github.leanish.sample;

            import org.jspecify.annotations.NullMarked;
            """.trimIndent(),
        )
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Greeting.java",
            """
            package io.github.leanish.sample;

            import org.jspecify.annotations.Nullable;

            import lombok.Builder;
            import lombok.Value;

            /** A greeting with an optional title. */
            @Value
            @Builder
            public class Greeting {
                private final String name;
                private final @Nullable String title;

                /** Renders the greeting, with the title when there is one. */
                public String render() {
                    String title = this.title;
                    if (title == null) {
                        return "Hello, " + name;
                    }
                    return "Hello, " + title + " " + name;
                }
            }
            """.trimIndent(),
        )
        writeSource(
            projectDir,
            "src/test/java/io/github/leanish/sample/GreetingTest.java",
            """
            package io.github.leanish.sample;

            import static org.assertj.core.api.Assertions.assertThat;

            import org.junit.jupiter.api.Test;

            import lombok.Value;

            class GreetingTest {
                @Test
                void rendersWithoutTitle() {
                    Expectation expectation = new Expectation("Ada", "Hello, Ada");

                    Greeting greeting = Greeting.builder().name(expectation.getName()).build();

                    assertThat(greeting.render()).isEqualTo(expectation.getRendered());
                }

                @Test
                void rendersWithTitle() {
                    Greeting greeting = Greeting.builder().name("Lovelace").title("Countess").build();

                    assertThat(greeting.render()).isEqualTo("Hello, Countess Lovelace");
                    assertThat(greeting.getTitle()).isEqualTo("Countess");
                }

                @Value
                static class Expectation {
                    private final String name;
                    private final String rendered;
                }
            }
            """.trimIndent(),
        )
        return projectDir
    }
}
