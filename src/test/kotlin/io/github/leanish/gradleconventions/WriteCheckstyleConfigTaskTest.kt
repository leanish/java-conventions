package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

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
    fun copiesTheWholeConsumerConfigDirectoryAndDropsStaleFiles() {
        val project = newProject(tempDir.resolve("auxiliary").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))
        val configDir = project.layout.projectDirectory.dir("config/checkstyle").asFile
        configDir.resolve("nested").mkdirs()
        configDir.resolve("checkstyle.xml").writeText("<module name=\"Checker\"/>")
        configDir.resolve("import-control.xml").writeText("<import-control pkg=\"x\"/>")
        configDir.resolve("nested/header.txt").writeText("header")
        task.consumerConfigDirectory.set(configDir)
        task.consumerCheckstyleFile.set(configDir.resolve("checkstyle.xml"))

        task.writeCheckstyleFiles()
        val generatedDir = project.layout.buildDirectory.dir("generated/checkstyle").get().asFile
        assertThat(generatedDir.resolve("import-control.xml")).hasContent("<import-control pkg=\"x\"/>")
        assertThat(generatedDir.resolve("nested/header.txt")).hasContent("header")
        assertThat(generatedDir.resolve("suppressions.xml").readText()).contains("<suppressions>")

        configDir.resolve("import-control.xml").delete()
        task.writeCheckstyleFiles()
        assertThat(generatedDir.resolve("import-control.xml")).doesNotExist()
        assertThat(generatedDir.resolve("checkstyle.xml")).hasContent("<module name=\"Checker\"/>")
    }

    private fun newProject(projectDir: File): Project {
        projectDir.mkdirs()
        return ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
    }
}
