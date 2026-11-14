/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import org.w3c.dom.Element
import java.io.Serializable

/** A POM `<developer>` the conventions infer from the publishing properties or the GitHub owner. */
internal data class PomDeveloper(val id: String, val name: String, val url: String) : Serializable

/** POM elements that come after `<developers>` in Maven's model order. */
private val ELEMENTS_AFTER_DEVELOPERS = setOf(
    "contributors", "mailingLists", "prerequisites", "modules", "scm", "issueManagement", "ciManagement",
    "distributionManagement", "properties", "dependencyManagement", "dependencies", "repositories",
    "pluginRepositories", "build", "reporting", "profiles",
)

/**
 * Adds [developer] to the generated POM [project] element unless it already lists a developer, keeping Maven's element
 * order (a new `<developers>` goes before the first element that follows it).
 */
internal fun addDeveloperIfNone(project: Element, developer: PomDeveloper) {
    val existing = childElements(project).firstOrNull { it.localNameOrTag() == "developers" }
    if (existing != null && childElements(existing).any { it.localNameOrTag() == "developer" }) {
        return
    }
    val document = project.ownerDocument
    val namespace = project.namespaceURI
    // In the POM's own namespace; a namespace-less element would be written with xmlns="".
    fun element(name: String, text: String? = null): Element =
        (if (namespace == null) document.createElement(name) else document.createElementNS(namespace, name))
            .also { if (text != null) it.textContent = text }

    val developers = existing ?: element("developers").also { created ->
        val next = childElements(project).firstOrNull { it.localNameOrTag() in ELEMENTS_AFTER_DEVELOPERS }
        project.insertBefore(created, next)
    }
    developers.appendChild(
        element("developer").apply {
            appendChild(element("id", developer.id))
            appendChild(element("name", developer.name))
            appendChild(element("url", developer.url))
        },
    )
}

private fun childElements(parent: Element): List<Element> =
    (0 until parent.childNodes.length).map(parent.childNodes::item).filterIsInstance<Element>()

private fun Element.localNameOrTag(): String = localName ?: tagName
