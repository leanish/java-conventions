package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
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

    @Test
    fun skipsSymbolicLinksInTheConsumerConfigDirectory() {
        val project = newProject(tempDir.resolve("links").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))
        val configDir = project.layout.projectDirectory.dir("config/checkstyle").asFile.apply { mkdirs() }
        configDir.resolve("import-control.xml").writeText("<import-control pkg=\"x\"/>")
        val outside = tempDir.resolve("outside").toFile().apply { mkdirs() }
        outside.resolve("secret.txt").writeText("outside")
        Files.createSymbolicLink(configDir.resolve("loop").toPath(), configDir.toPath())
        Files.createSymbolicLink(configDir.resolve("external").toPath(), outside.toPath())
        Files.createSymbolicLink(configDir.resolve("linked.txt").toPath(), outside.resolve("secret.txt").toPath())
        task.consumerConfigDirectory.set(configDir)

        task.writeCheckstyleFiles()

        val generatedDir = project.layout.buildDirectory.dir("generated/checkstyle").get().asFile
        assertThat(generatedDir.resolve("import-control.xml")).exists()
        assertThat(generatedDir.resolve("loop")).doesNotExist()
        assertThat(generatedDir.resolve("external")).doesNotExist()
        assertThat(generatedDir.resolve("linked.txt")).doesNotExist()
        assertThat(outside.resolve("secret.txt")).hasContent("outside")
    }

    @Test
    fun readsLinkedCheckstyleAndSuppressionsFiles() {
        val project = newProject(tempDir.resolve("linked-named-files").toFile())
        val task = project.tasks.register("writeCheckstyleConfig", WriteCheckstyleConfigTask::class.java).get()
        task.outputDir.set(project.layout.buildDirectory.dir("generated/checkstyle"))
        val shared = tempDir.resolve("shared").toFile().apply { mkdirs() }
        shared.resolve("checkstyle.xml").writeText("<module name=\"Checker\"><!-- shared --></module>")
        shared.resolve("suppressions.xml").writeText("<suppressions><!-- shared --></suppressions>")
        val configDir = project.layout.projectDirectory.dir("config/checkstyle").asFile.apply { mkdirs() }
        Files.createSymbolicLink(configDir.resolve("checkstyle.xml").toPath(), shared.resolve("checkstyle.xml").toPath())
        Files.createSymbolicLink(configDir.resolve("suppressions.xml").toPath(), shared.resolve("suppressions.xml").toPath())
        task.consumerConfigDirectory.set(configDir)
        task.consumerCheckstyleFile.set(configDir.resolve("checkstyle.xml"))
        task.consumerSuppressionsFile.set(configDir.resolve("suppressions.xml"))

        task.writeCheckstyleFiles()

        val generatedDir = project.layout.buildDirectory.dir("generated/checkstyle").get().asFile
        assertThat(generatedDir.resolve("checkstyle.xml")).hasContent("<module name=\"Checker\"><!-- shared --></module>")
        assertThat(generatedDir.resolve("suppressions.xml")).hasContent("<suppressions><!-- shared --></suppressions>")
    }

    private fun newProject(projectDir: File): Project {
        projectDir.mkdirs()
        return ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
    }
}
