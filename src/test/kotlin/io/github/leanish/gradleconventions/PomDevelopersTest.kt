package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class PomDevelopersTest {
    private val inferred = PomDeveloper("owner", "Owner", "https://github.com/owner")

    private fun project(body: String, namespaceAware: Boolean = true): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = namespaceAware }
        val xml = """<project xmlns="http://maven.apache.org/POM/4.0.0">$body</project>"""
        return factory.newDocumentBuilder().parse(xml.byteInputStream()).documentElement
    }

    private fun Element.serialized(): String {
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
            .transform(DOMSource(this), StreamResult(writer))
        return writer.toString()
    }

    @Test
    fun addsTheDeveloperBeforeTheElementsThatFollowDevelopers() {
        val project = project("<licenses/><scm/><dependencies/>")

        addDeveloperIfNone(project, inferred)

        assertThat(project.serialized()).isEqualTo(
            """<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses/><developers><developer><id>owner</id>""" +
                """<name>Owner</name><url>https://github.com/owner</url></developer></developers><scm/><dependencies/></project>""",
        )
    }

    @Test
    fun appendsDevelopersWhenNothingFollowsThem() {
        val project = project("<name>x</name>", namespaceAware = false)

        addDeveloperIfNone(project, inferred)

        assertThat(project.serialized()).endsWith("<name>x</name><developers><developer><id>owner</id><name>Owner</name><url>https://github.com/owner</url></developer></developers></project>")
    }

    @Test
    fun fillsAnEmptyDevelopersElement() {
        val project = project("<developers/><scm/>")

        addDeveloperIfNone(project, inferred)

        assertThat(project.getElementsByTagName("developer").length).isEqualTo(1)
        assertThat(project.getElementsByTagName("developers").length).isEqualTo(1)
    }

    @Test
    fun leavesDevelopersTheBuildDeclared() {
        val project = project("<developers><developer><id>me</id></developer></developers>")

        addDeveloperIfNone(project, inferred)

        assertThat(project.getElementsByTagName("developer").length).isEqualTo(1)
        assertThat(project.serialized()).contains("<id>me</id>").doesNotContain("owner")
    }
}
