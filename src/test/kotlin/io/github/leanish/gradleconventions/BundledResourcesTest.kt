package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BundledResourcesTest {
    @Test
    fun loadThrowsClearErrorWhenResourceIsMissing() {
        assertThatThrownBy { BundledResources.load("checkstyle/not-found.xml") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Missing bundled resource at 'checkstyle/not-found.xml'")
    }
}
