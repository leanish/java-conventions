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
    fun pluginScriptTakesInjectedVersionsFromTheCatalog() {
        val script = File("src/main/kotlin/io.github.leanish.java-conventions.gradle.kts").readText()

        // A dependency notation or tool version written as a literal would bypass the catalog (and Dependabot).
        assertThat(Regex("\"[\\w.-]+:[\\w.-]+:\\d").findAll(script).map { it.value }.toList()).isEmpty()
        assertThat(Regex("(toolVersion\\s*=|Version\\.set\\()\\s*\"").findAll(script).map { it.value }.toList()).isEmpty()
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
