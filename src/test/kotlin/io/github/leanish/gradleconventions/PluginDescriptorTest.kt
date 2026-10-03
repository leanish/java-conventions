package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

class PluginDescriptorTest {
    @Test
    fun declaresConfigurationCacheSupport() {
        val descriptor = loadDescriptor()

        assertThat(descriptor.getProperty("compatibility.feature.configuration-cache"))
            .isEqualTo("DECLARED_SUPPORTED")
    }

    private fun loadDescriptor(): Properties {
        val path = "META-INF/gradle-plugins/io.github.leanish.java-conventions.properties"
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing plugin descriptor at '$path'"
        }
        return stream.use { Properties().apply { load(it) } }
    }
}
