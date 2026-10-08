package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class ConventionVersionsTest {
    @Test
    fun generatedConstantsMatchTheCatalog() {
        val catalogVersions = readCatalogVersions()

        assertThat(
            mapOf(
                "assertj" to ConventionVersions.ASSERTJ,
                "checkstyle" to ConventionVersions.CHECKSTYLE,
                "errorprone" to ConventionVersions.ERROR_PRONE,
                "guava" to ConventionVersions.GUAVA,
                "jacoco" to ConventionVersions.JACOCO,
                "jetbrains-annotations" to ConventionVersions.JETBRAINS_ANNOTATIONS,
                "jspecify" to ConventionVersions.JSPECIFY,
                "junit" to ConventionVersions.JUNIT,
                "lombok" to ConventionVersions.LOMBOK,
                "nullaway" to ConventionVersions.NULLAWAY,
                "pitest" to ConventionVersions.PITEST,
                "pitest-junit5-plugin" to ConventionVersions.PITEST_JUNIT5_PLUGIN,
                "pitest-gradle-plugin" to ConventionVersions.PITEST_GRADLE_PLUGIN,
            ),
        ).allSatisfy { alias, version -> assertThat(version).isEqualTo(catalogVersions[alias]) }
    }

    @Test
    fun mainSourcesTakeInjectedVersionsFromTheCatalog() {
        // A dependency notation or tool version written as a literal would bypass the catalog (and Dependabot).
        val literalVersions = listOf(
            Regex("\"[\\w.-]+:[\\w.-]+:\\d"),
            Regex("(toolVersion\\s*=|Version\\.set\\()\\s*\""),
        )
        val sources = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.name.endsWith(".gradle.kts")) }
            .toList()

        assertThat(sources).isNotEmpty()
        assertThat(
            sources.flatMap { source ->
                val text = source.readText()
                literalVersions.flatMap { pattern -> pattern.findAll(text).map { "${source.name}: ${it.value}" } }
            },
        ).isEmpty()
    }

    private fun readCatalogVersions(): Map<String, String> {
        val entry = Regex("^([\\w-]+)\\s*=\\s*\"([^\"]+)\"")
        return File("gradle/libs.versions.toml").readLines()
            .dropWhile { it.trim() != "[versions]" }
            .drop(1)
            .takeWhile { !it.trim().startsWith("[") }
            .mapNotNull { line -> entry.find(line.trim())?.destructured }
            .associate { (alias, version) -> alias to version }
    }
}
