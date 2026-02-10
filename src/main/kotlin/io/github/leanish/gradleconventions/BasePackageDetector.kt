/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.io.File
import org.gradle.api.Project

internal object BasePackageDetector {
    private val packageRegex = Regex("""^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*;""")

    fun detectBasePackages(projectDir: File): List<String> {
        val sourceRoot = projectDir.resolve("src/main/java")
        if (!sourceRoot.exists() || !sourceRoot.isDirectory) {
            return emptyList()
        }

        val detectedPackages = mutableSetOf<String>()
        detectPackagesRecursively(
            directory = sourceRoot,
            detectedPackages = detectedPackages,
        )

        val rootPackages = mutableListOf<String>()
        detectedPackages.sorted().forEach { packageName ->
            // Keep only top-level roots: once "com.example" is accepted, skip "com.example.*"
            // but still keep sibling prefixes like "com.example2" (covered by tests).
            if (rootPackages.none { parent -> packageName == parent || packageName.startsWith("$parent.") }) {
                rootPackages.add(packageName)
            }
        }
        return rootPackages
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
            .filter(File::isDirectory)
            .forEach { childDirectory ->
                detectPackagesRecursively(
                    directory = childDirectory,
                    detectedPackages = detectedPackages,
                )
            }
    }

    private fun detectPackageNameFromSourceFile(sourceFile: File): String? {
        sourceFile.useLines { lines ->
            return lines.firstNotNullOfOrNull { line ->
                packageRegex.find(line)?.groupValues?.get(1)
            }
        }
    }

}

internal fun Project.detectBasePackages(): List<String> {
    return BasePackageDetector.detectBasePackages(layout.projectDirectory.asFile)
}
