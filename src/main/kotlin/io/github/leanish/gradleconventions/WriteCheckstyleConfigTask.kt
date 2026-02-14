/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Materializes the Checkstyle files used by conventions into the build directory.
 *
 * Responsibilities:
 * - Copies the consumer project's `checkstyle.xml` into the generated config directory when provided.
 * - Falls back to the plugin-bundled `checkstyle.xml` when no project file exists.
 * - Copies the consumer project's `suppressions.xml` into the generated config directory when provided.
 * - Falls back to the plugin-bundled empty suppressions file when no project file exists.
 *
 * Uses a typed task (with declared inputs/outputs) so Gradle can track incremental work.
 * Generated files are build-time Checkstyle inputs only and are not packaged into consumer deliverables.
 */
internal abstract class WriteCheckstyleConfigTask : DefaultTask() {
    /** Target directory where generated Checkstyle files are written. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Optional consumer suppressions file (`config/checkstyle/suppressions.xml`). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerSuppressionsFile: RegularFileProperty

    /** Optional consumer Checkstyle config file (`config/checkstyle/checkstyle.xml`). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerCheckstyleFile: RegularFileProperty

    /** Materializes `checkstyle.xml` and `suppressions.xml` into [outputDir]. */
    @TaskAction
    fun writeCheckstyleFiles() {
        val outputDirFile = outputDir.get().asFile
        outputDirFile.mkdirs()

        val checkstyleConfigFile = outputDir.file("checkstyle.xml").get().asFile
        val consumerCheckstyleInputFile = consumerCheckstyleFile.orNull?.asFile
        if (consumerCheckstyleInputFile != null) {
            checkstyleConfigFile.writeText(consumerCheckstyleInputFile.readText())
        } else {
            checkstyleConfigFile.writeText(loadRequiredResource("checkstyle/checkstyle.xml"))
        }

        val suppressionsFile = outputDir.file("suppressions.xml").get().asFile
        val consumerSuppressionsInputFile = consumerSuppressionsFile.orNull?.asFile
        if (consumerSuppressionsInputFile != null) {
            suppressionsFile.writeText(consumerSuppressionsInputFile.readText())
        } else {
            suppressionsFile.writeText(loadRequiredResource("checkstyle/empty-suppressions.xml"))
        }
    }

    private fun loadRequiredResource(path: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource(path)) {
            "Missing bundled resource at '$path'"
        }
        return resource.readText()
    }
}
