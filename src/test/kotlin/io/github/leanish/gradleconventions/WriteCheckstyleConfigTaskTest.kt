package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WriteCheckstyleConfigTaskTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun writesBundledCheckstyleAndEmptySuppressionsWhenProjectFileIsMissing() {
        val project = newProject(tempDir.resolve("bundled").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))

        task.writeCheckstyleFiles()

        val generatedDir = project.layout.buildDirectory.dir("generated/checkstyle").get().asFile
        val generatedCheckstyle = generatedDir.resolve("checkstyle.xml").readText()
        val generatedSuppressions = generatedDir.resolve("suppressions.xml").readText()

        assertThat(generatedCheckstyle).contains("<module name=\"Checker\">")
        assertThat(generatedSuppressions).contains("<suppressions>")
    }

    @Test
    fun writesProjectCheckstyleWhenProvided() {
        val project = newProject(tempDir.resolve("custom-checkstyle").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))

        val customCheckstyle = project.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
        customCheckstyle.parentFile.mkdirs()
        customCheckstyle.writeText("<module name=\"Checker\"><module name=\"TreeWalker\"/></module>")
        task.consumerCheckstyleFile.set(customCheckstyle)

        task.writeCheckstyleFiles()

        val generatedCheckstyle = project.layout.buildDirectory
            .file("generated/checkstyle/checkstyle.xml")
            .get()
            .asFile
            .readText()

        assertThat(generatedCheckstyle).isEqualTo("<module name=\"Checker\"><module name=\"TreeWalker\"/></module>")
    }

    @Test
    fun writesProjectSuppressionsWhenProvided() {
        val project = newProject(tempDir.resolve("custom").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))

        val customSuppressions = project.layout.projectDirectory.file("config/checkstyle/suppressions.xml").asFile
        customSuppressions.parentFile.mkdirs()
        customSuppressions.writeText("<suppressions><suppress files=\".*\"/></suppressions>")
        task.consumerSuppressionsFile.set(customSuppressions)

        task.writeCheckstyleFiles()

        val generatedSuppressions = project.layout.buildDirectory
            .file("generated/checkstyle/suppressions.xml")
            .get()
            .asFile
            .readText()

        assertThat(generatedSuppressions).isEqualTo("<suppressions><suppress files=\".*\"/></suppressions>")
    }

    @Test
    fun fallsBackToBundledCheckstyleWhenConsumerCheckstyleIsNotConfigured() {
        val project = newProject(tempDir.resolve("missing-checkstyle").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))

        val customSuppressions = project.layout.projectDirectory.file("config/checkstyle/suppressions.xml").asFile
        customSuppressions.parentFile.mkdirs()
        customSuppressions.writeText("<suppressions><suppress files=\".*\"/></suppressions>")
        task.consumerSuppressionsFile.set(customSuppressions)

        task.writeCheckstyleFiles()

        val generatedCheckstyle = project.layout.buildDirectory
            .file("generated/checkstyle/checkstyle.xml")
            .get()
            .asFile
            .readText()
        val generatedSuppressions = project.layout.buildDirectory
            .file("generated/checkstyle/suppressions.xml")
            .get()
            .asFile
            .readText()

        assertThat(generatedCheckstyle).contains("<module name=\"Checker\">")
        assertThat(generatedSuppressions).isEqualTo("<suppressions><suppress files=\".*\"/></suppressions>")
    }

    @Test
    fun fallsBackToBundledSuppressionsWhenConsumerSuppressionsIsNotConfigured() {
        val project = newProject(tempDir.resolve("missing-suppressions").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))

        val customCheckstyle = project.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
        customCheckstyle.parentFile.mkdirs()
        customCheckstyle.writeText("<module name=\"Checker\"><module name=\"TreeWalker\"/></module>")
        task.consumerCheckstyleFile.set(customCheckstyle)

        task.writeCheckstyleFiles()

        val generatedCheckstyle = project.layout.buildDirectory
            .file("generated/checkstyle/checkstyle.xml")
            .get()
            .asFile
            .readText()
        val generatedSuppressions = project.layout.buildDirectory
            .file("generated/checkstyle/suppressions.xml")
            .get()
            .asFile
            .readText()

        assertThat(generatedCheckstyle).isEqualTo("<module name=\"Checker\"><module name=\"TreeWalker\"/></module>")
        assertThat(generatedSuppressions).contains("<suppressions>")
    }

    @Test
    fun throwsClearErrorWhenBundledResourceIsMissing() {
        val project = newProject(tempDir.resolve("missing-resource").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()

        val loadRequiredResource = WriteCheckstyleConfigTask::class.java.getDeclaredMethod(
            "loadRequiredResource",
            String::class.java,
        )
        loadRequiredResource.isAccessible = true

        assertThatThrownBy {
            loadRequiredResource.invoke(task, "checkstyle/not-found.xml")
        }
            .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            .hasRootCauseMessage("Missing bundled resource at 'checkstyle/not-found.xml'")
    }

    private fun newProject(projectDir: File): Project {
        projectDir.mkdirs()
        return ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
    }
}
