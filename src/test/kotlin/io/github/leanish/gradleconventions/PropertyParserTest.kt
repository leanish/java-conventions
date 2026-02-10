package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

class PropertyParserTest {
    @Test
    fun booleanPropertyReturnsDefaultWhenMissing() {
        assertThat(
            PropertyParser.booleanProperty(
                name = "feature.enabled",
                value = null,
                defaultValue = true,
            ),
        ).isTrue()

        assertThat(
            PropertyParser.booleanProperty(
                name = "feature.enabled",
                value = null,
                defaultValue = false,
            ),
        ).isFalse()
    }

    @Test
    fun booleanPropertyParsesConfiguredValue() {
        assertThat(
            PropertyParser.booleanProperty(
                name = "feature.enabled",
                value = " true ",
                defaultValue = false,
            ),
        ).isTrue()

        assertThat(
            PropertyParser.booleanProperty(
                name = "feature.enabled",
                value = "FALSE",
                defaultValue = true,
            ),
        ).isFalse()
    }

    @Test
    fun booleanPropertyRejectsInvalidValue() {
        assertThatThrownBy {
            PropertyParser.booleanProperty(
                name = "feature.enabled",
                value = "invalid",
                defaultValue = true,
            )
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("Property 'feature.enabled' must be 'true' or 'false', got 'invalid'")
    }

    @Test
    fun stringPropertyReturnsNullWhenMissing() {
        assertThat(
            PropertyParser.stringProperty(
                name = "prop",
                value = null,
            ),
        ).isNull()
    }

    @Test
    fun stringPropertyReturnsTrimmedValue() {
        assertThat(
            PropertyParser.stringProperty(
                name = "prop",
                value = "  value  ",
            ),
        ).isEqualTo("value")
    }

    @Test
    fun stringPropertyRejectsBlankValue() {
        assertThatThrownBy {
            PropertyParser.stringProperty(
                name = "prop",
                value = "   ",
            )
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("Property 'prop' must not be blank")
    }
}
