/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes

/**
 * Materializes the Checkstyle files used by conventions into the build directory.
 *
 * Responsibilities:
 * - Copies the consumer project's whole `config/checkstyle` directory, keeping its layout, so files a custom config
 *   references through `${config_loc}` (an import-control file, say) resolve in the generated directory. Only regular
 *   files and real directories are copied: symbolic links in it are skipped with a warning, so a link can't pull in an
 *   outside tree or loop back on itself. The two named files below are the exception: they are read even through a
 *   link.
 * - Copies the consumer project's `checkstyle.xml` into the generated config directory when provided.
 * - Falls back to the plugin-bundled `checkstyle.xml` when no project file exists.
 * - Copies the consumer project's `suppressions.xml` into the generated config directory when provided.
 * - Falls back to the plugin-bundled empty suppressions file when no project file exists.
 *
 * Uses a typed task (with declared inputs/outputs) so Gradle can track incremental work and reuse build cache entries.
 * Generated files are build-time Checkstyle inputs only and are not packaged into consumer deliverables.
 */
@CacheableTask
internal abstract class WriteCheckstyleConfigTask : DefaultTask() {
    /** Target directory where generated Checkstyle files are written. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Optional consumer suppressions file (`config/checkstyle/suppressions.xml`). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerSuppressionsFile: RegularFileProperty

    /** Optional consumer Checkstyle directory (`config/checkstyle`), copied whole into [outputDir]. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerConfigDirectory: DirectoryProperty

    /** Optional consumer Checkstyle config file (`config/checkstyle/checkstyle.xml`). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerCheckstyleFile: RegularFileProperty

    /**
     * Rebuilds [outputDir]: the regular files of [consumerConfigDirectory] with their layout, then `checkstyle.xml` and
     * `suppressions.xml` (the consumer's when provided, else the bundled ones).
     */
    @TaskAction
    fun writeCheckstyleFiles() {
        val outputDirFile = outputDir.get().asFile
        // Start empty, so a file deleted from config/checkstyle doesn't linger in the generated directory.
        outputDirFile.deleteRecursively()
        outputDirFile.mkdirs()
        consumerConfigDirectory.orNull?.asFile?.let { source ->
            // checkstyle.xml and suppressions.xml are written below from their own inputs, links or not.
            val skipped = copyRegularFiles(source.toPath(), outputDirFile.toPath())
                .filterNot { it.toString() in NAMED_FILES }
            if (skipped.isNotEmpty()) {
                logger.warn(
                    "Skipped symbolic links while copying $source: ${skipped.joinToString()}. Only regular files are " +
                        "copied for Checkstyle (checkstyle.xml and suppressions.xml are read even through a link).",
                )
            }
        }

        writeGeneratedFile(
            fileName = "checkstyle.xml",
            consumerFile = consumerCheckstyleFile.orNull?.asFile,
            bundledResourcePath = "checkstyle/checkstyle.xml",
        )
        writeGeneratedFile(
            fileName = "suppressions.xml",
            consumerFile = consumerSuppressionsFile.orNull?.asFile,
            bundledResourcePath = "checkstyle/empty-suppressions.xml",
        )
    }

    private companion object {
        val NAMED_FILES = setOf("checkstyle.xml", "suppressions.xml")
    }

    /** Copies [source]'s regular files into [target] without following links; returns the links it skipped. */
    private fun copyRegularFiles(source: Path, target: Path): List<Path> {
        val skipped = mutableListOf<Path>()
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.createDirectories(target.resolve(source.relativize(directory).toString()))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isRegularFile) {
                        Files.copy(file, target.resolve(source.relativize(file).toString()), REPLACE_EXISTING, NOFOLLOW_LINKS)
                    } else {
                        // add(), not +=: a Path is also an Iterable<Path>, which += would spread.
                        skipped.add(source.relativize(file))
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return skipped.sorted()
    }

    private fun writeGeneratedFile(
        fileName: String,
        consumerFile: File?,
        bundledResourcePath: String,
    ) {
        val content = consumerFile?.readText(UTF_8) ?: BundledResources.load(bundledResourcePath)
        outputDir.file(fileName).get().asFile.writeText(content, UTF_8)
    }
}
