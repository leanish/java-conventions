/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.gradle.api.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal object BasePackageDetector {
    fun detectBasePackages(projectDir: File): List<String> {
        val sourceRoot = projectDir.resolve("src/main/java")
        if (!sourceRoot.isRealDirectory()) {
            return emptyList()
        }

        val detectedPackages = mutableSetOf<String>()
        detectPackagesRecursively(
            directory = sourceRoot,
            detectedPackages = detectedPackages,
        )

        return topLevelPackages(detectedPackages)
    }

    private fun topLevelPackages(detectedPackages: Set<String>): List<String> {
        val rootPackages = mutableListOf<String>()
        detectedPackages.sorted().forEach { packageName ->
            // Treat only exact matches and dot-qualified descendants as nested so
            // sibling prefixes like "com.example2" remain distinct from "com.example".
            if (rootPackages.none { rootPackage -> packageName.isSamePackageOrNestedUnder(rootPackage) }) {
                rootPackages.add(packageName)
            }
        }
        return rootPackages
    }

    private fun String.isSamePackageOrNestedUnder(rootPackage: String): Boolean {
        return this == rootPackage || startsWith("$rootPackage.")
    }

    private fun detectPackagesRecursively(
        directory: File,
        detectedPackages: MutableSet<String>,
    ) {
        val directoryEntries = directory.listFiles()?.toList() ?: return
        val packageInDirectory = directoryEntries.asSequence()
            .filter { entry -> entry.isFile && entry.extension == "java" }
            .firstNotNullOfOrNull(::detectPackageNameFromSourceFile)

        if (packageInDirectory != null) {
            detectedPackages.add(packageInDirectory)
            return
        }

        directoryEntries.asSequence()
            .filter { entry -> entry.isRealDirectory() }
            .forEach { childDirectory ->
                detectPackagesRecursively(
                    directory = childDirectory,
                    detectedPackages = detectedPackages,
                )
            }
    }

    /**
     * A directory that isn't reached through a symbolic link. Links are skipped so a link to an ancestor can't make the
     * walk revisit the tree over and over; a linked source tree has to set `leanish.conventions.basePackage`.
     */
    private fun File.isRealDirectory(): Boolean =
        Files.isDirectory(toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun detectPackageNameFromSourceFile(sourceFile: File): String? {
        return JavaPackageDeclaration.parse(sourceFile.readText())
    }
}

internal fun Project.detectBasePackages(): List<String> {
    return BasePackageDetector.detectBasePackages(layout.projectDirectory.asFile)
}
