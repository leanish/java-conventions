package io.github.leanish.gradleconventions

import java.io.File

internal fun writeFile(projectDir: File, name: String, content: String) {
    val file = projectDir.resolve(name)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

internal fun writeRequiredConventionsProperties(projectDir: File) {
    writeFile(
        projectDir,
        "gradle.properties",
        "leanish.conventions.basePackage=io.github.leanish",
    )
}
