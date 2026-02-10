package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class PropertyParserProjectExtensionsTest {
    @Test
    fun booleanPropertyUsesDefaultWhenMissing() {
        val project = newProject()

        assertThat(project.booleanProperty(name = "feature.enabled", defaultValue = true).get()).isTrue()
        assertThat(project.booleanProperty(name = "feature.enabled", defaultValue = false).get()).isFalse()
    }

    @Test
    fun booleanPropertyUsesConfiguredValue() {
        val project = newProject()
        project.extensions.extraProperties.set("feature.enabled", " false ")

        assertThat(project.booleanProperty(name = "feature.enabled", defaultValue = true).get()).isFalse()
    }

    @Test
    fun booleanPropertyRejectsInvalidConfiguredValue() {
        val project = newProject()
        project.extensions.extraProperties.set("feature.enabled", "invalid")

        assertThatThrownBy {
            project.booleanProperty(name = "feature.enabled", defaultValue = true).get()
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("Property 'feature.enabled' must be 'true' or 'false', got 'invalid'")
    }

    @Test
    fun stringPropertyReturnsNullWhenMissing() {
        val project = newProject()

        assertThat(project.stringProperty("feature.name")).isNull()
    }

    @Test
    fun stringPropertyReturnsConfiguredValue() {
        val project = newProject()
        project.extensions.extraProperties.set("feature.name", "  parser ")

        assertThat(project.stringProperty("feature.name")).isEqualTo("parser")
    }

    @Test
    fun stringPropertyRejectsBlankConfiguredValue() {
        val project = newProject()
        project.extensions.extraProperties.set("feature.name", "   ")

        assertThatThrownBy {
            project.stringProperty("feature.name")
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("Property 'feature.name' must not be blank")
    }

    private fun newProject(): Project {
        return ProjectBuilder.builder().build()
    }
}
