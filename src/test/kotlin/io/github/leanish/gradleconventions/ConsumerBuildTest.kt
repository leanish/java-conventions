package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
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

    @Test
    fun customCheckstyleConfigCanReferenceFilesNextToItThroughConfigLoc() {
        val projectDir = sampleConsumer("consumer-checkstyle-config-loc")
        writeFile(
            projectDir,
            "config/checkstyle/checkstyle.xml",
            """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN" "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="TreeWalker">
                    <module name="ImportControl">
                        <property name="file" value="${'$'}{config_loc}/import-control.xml"/>
                    </module>
                </module>
            </module>
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "config/checkstyle/import-control.xml",
            """
            <?xml version="1.0"?>
            <!DOCTYPE import-control PUBLIC "-//Checkstyle//DTD ImportControl Configuration 1.4//EN" "https://checkstyle.org/dtds/import_control_1_4.dtd">
            <import-control pkg="io.github.leanish.sample" strategyOnMismatch="allowed">
                <disallow pkg="java.util"/>
            </import-control>
            """.trimIndent(),
        )
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Names.java",
            """
            package io.github.leanish.sample;

            import java.util.List;

            public final class Names {
                public List<String> all() {
                    return List.of("Ada");
                }
            }
            """.trimIndent(),
        )

        val result = runner(projectDir, "checkstyleMain").buildAndFail()

        assertThat(result.task(":checkstyleMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Disallowed import - java.util.List")
    }

    @Test
    fun errorProneFormatterParsesMarkdownJavadocWithTheCommonmarkFloor() {
        // google-java-format, on Error Prone's classpath, parses Markdown javadoc with commonmark; the conventions raise
        // commonmark above the version it was built against, so run it on a Markdown comment with a table.
        val projectDir = sampleConsumer("consumer-markdown-javadoc")
        writeSource(
            projectDir,
            "src/main/java/io/github/leanish/sample/Table.java",
            """
            package io.github.leanish.sample;

            /// Values by name.
            ///
            /// | Name | Value |
            /// |------|-------|
            /// | one  | 1     |
            public final class Table {}
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").appendText(
            """

            tasks.register<JavaExec>("formatMarkdownJavadoc") {
                classpath = configurations.getByName("annotationProcessor")
                mainClass.set("com.google.googlejavaformat.java.Main")
                jvmArgs(
                    listOf("api", "code", "file", "main", "parser", "tree", "util")
                        .map { "--add-exports=jdk.compiler/com.sun.tools.javac.${'$'}it=ALL-UNNAMED" } + "-verbose:class",
                )
                args("src/main/java/io/github/leanish/sample/Table.java")
            }
            """.trimIndent(),
        )

        val result = runner(projectDir, "formatMarkdownJavadoc").build()
        val version = Regex.escape(ConventionVersions.COMMONMARK)

        assertThat(result.task(":formatMarkdownJavadoc")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output)
            .containsPattern("org\\.commonmark\\.parser\\.Parser source: .*commonmark-$version\\.jar")
            .containsPattern(
                "org\\.commonmark\\.ext\\.gfm\\.tables\\.TableBlock source: .*commonmark-ext-gfm-tables-" +
                    "$version\\.jar",
            )
            .contains("/// | Name | Value |")
    }

    @Test
    fun writeCheckstyleConfigTracksAuxiliaryFilesAndSkipsLinks() {
        val projectDir = sampleConsumer("consumer-checkstyle-auxiliary-tracking")
        val auxiliary = projectDir.resolve("config/checkstyle/import-control.xml")
        val generated = projectDir.resolve("build/generated/checkstyle/import-control.xml")
        writeFile(projectDir, "config/checkstyle/import-control.xml", "v1")
        fun write() = runner(projectDir, "writeCheckstyleConfig").build()

        assertThat(write().task(":writeCheckstyleConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated).hasContent("v1")
        assertThat(write().task(":writeCheckstyleConfig")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

        auxiliary.writeText("v2")
        assertThat(write().task(":writeCheckstyleConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated).hasContent("v2")

        auxiliary.delete()
        assertThat(write().task(":writeCheckstyleConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated).doesNotExist()

        // Through Gradle too, so its snapshot of the input directory meets them: a loop, an outside tree, a linked file.
        val configDir = projectDir.resolve("config/checkstyle").toPath()
        val outside = tempDir.resolve("outside-config").toFile().apply { mkdirs() }
        outside.resolve("secret.txt").writeText("outside")
        Files.createSymbolicLink(configDir.resolve("loop"), configDir)
        Files.createSymbolicLink(configDir.resolve("external"), outside.toPath())
        Files.createSymbolicLink(configDir.resolve("linked.txt"), outside.resolve("secret.txt").toPath())
        val withLinks = write()
        assertThat(withLinks.task(":writeCheckstyleConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(withLinks.output).contains("Skipped symbolic links in").contains("external, linked.txt, loop")
        assertThat(projectDir.resolve("build/generated/checkstyle/external")).doesNotExist()
        assertThat(projectDir.resolve("build/generated/checkstyle/linked.txt")).doesNotExist()
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
