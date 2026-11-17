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

internal fun environmentWithoutConventionsOverrides(
    overrides: Map<String, String> = emptyMap(),
): Map<String, String> {
    val baseEnvironment = System.getenv()
        .filterKeys { key ->
            !key.startsWith("JAVA_CONVENTIONS_") &&
                key != ConventionProperties.GITHUB_REPOSITORY_OWNER_ENV &&
                key != ConventionProperties.GITHUB_ACTOR_ENV &&
                key != ConventionProperties.GITHUB_TOKEN_ENV
        }
        .toMutableMap()
    baseEnvironment.putAll(overrides)
    return baseEnvironment
}

/** Runs git in [directory] (with a fixed identity, no global or system config) and returns its standard output. */
internal fun git(directory: File, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(directory)
        .redirectErrorStream(true)
        .apply {
            environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
            environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            environment()["GIT_AUTHOR_NAME"] = "Test"
            environment()["GIT_AUTHOR_EMAIL"] = "test@example.com"
            environment()["GIT_COMMITTER_NAME"] = "Test"
            environment()["GIT_COMMITTER_EMAIL"] = "test@example.com"
        }
        .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed in $directory: $output" }
    return output
}

/** A git repository with one empty commit, so worktrees can be added. */
internal fun initGitRepository(directory: File) {
    directory.mkdirs()
    git(directory, "init", "-q", "-b", "main")
    git(directory, "commit", "-q", "--allow-empty", "-m", "init")
}
