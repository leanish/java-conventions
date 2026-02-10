package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BasePackageDetectorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun detectBasePackagesReturnsEmptyWhenSourceRootIsMissing() {
        val missingProjectDir = tempDir.resolve("missing-root").toFile()
        missingProjectDir.mkdirs()

        assertThat(
            BasePackageDetector.detectBasePackages(missingProjectDir),
        ).isEmpty()
    }

    @Test
    fun detectBasePackagesDetectsTopLevelPackages() {
        val projectDir = tempDir.resolve("detect-packages").toFile()
        writeFile(
            projectDir,
            "src/main/java/com/example/app/Sample.java",
            """
            package com.example.app;
            public class Sample {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/app/internal/Nested.java",
            """
            package com.example.app.internal;
            public class Nested {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/other/Other.java",
            """
            package com.example.other;
            public class Other {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/org/acme/Tool.java",
            """
            package org.acme;
            public class Tool {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/NoPackage.java",
            """
            public class NoPackage {}
            """.trimIndent(),
        )

        assertThat(
            BasePackageDetector.detectBasePackages(projectDir),
        ).containsExactly("com.example.app", "com.example.other", "org.acme")
    }

    @Test
    fun detectBasePackagesStopsDescendingWhenPackageIsFoundInDirectory() {
        val projectDir = tempDir.resolve("short-circuit-branch").toFile()
        writeFile(
            projectDir,
            "src/main/java/com/example/Root.java",
            """
            package com.example;
            public class Root {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/com/example/internal/Unexpected.java",
            """
            package org.unrelated;
            public class Unexpected {}
            """.trimIndent(),
        )

        assertThat(
            BasePackageDetector.detectBasePackages(projectDir),
        ).containsExactly("com.example")
    }

    @Test
    fun detectBasePackagesKeepsDistinctPrefixPackages() {
        val projectDir = tempDir.resolve("distinct-prefix-packages").toFile()
        writeFile(
            projectDir,
            "src/main/java/legacy/Legacy.java",
            """
            package com.example;
            public class Legacy {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/newcode/Foo.java",
            """
            package com.example.foo;
            public class Foo {}
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/java/other/Bar.java",
            """
            package com.example2;
            public class Bar {}
            """.trimIndent(),
        )

        assertThat(
            BasePackageDetector.detectBasePackages(projectDir),
        ).containsExactly("com.example", "com.example2")
    }

    @Test
    fun detectBasePackagesExtensionUsesProjectDirectory() {
        val projectDir = tempDir.resolve("project-extension").toFile()
        writeFile(
            projectDir,
            "src/main/java/io/github/acme/App.java",
            """
            package io.github.acme;
            public class App {}
            """.trimIndent(),
        )
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()

        assertThat(project.detectBasePackages()).containsExactly("io.github.acme")
    }

    private fun writeFile(projectDir: File, name: String, content: String) {
        val file = projectDir.resolve(name)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
