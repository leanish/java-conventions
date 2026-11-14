/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

/** What [GitHookInstaller.install] did with the destination hook. */
internal enum class HookInstallOutcome {
    /** No hook was there; the source was written. */
    INSTALLED,

    /** The hook was the one this installer last wrote, unedited; it was replaced by the source. */
    UPDATED,

    /** The hook already had the source's bytes. */
    UNCHANGED,

    /** The hook wasn't written by this installer, or was edited since; it was left as is. */
    KEPT_UNMANAGED,

    /** The hook is a symbolic link; it was left as is. */
    KEPT_SYMLINK,
}

/**
 * Installs the pre-commit hook without overwriting one the developer owns.
 *
 * Next to the hook it keeps a record of the SHA-256 of the bytes it last wrote. A hook it may replace is one that is
 * absent, already identical, or still matching that record; anything else (a hook from another tool, a hand-written
 * one, an installed hook edited locally, a symbolic link) is kept.
 */
internal object GitHookInstaller {
    const val HOOK_NAME = "pre-commit"

    /** Git only runs files named after hooks, so the record in the hooks directory is inert. */
    const val RECORD_NAME = "pre-commit.java-conventions.sha256"

    fun install(source: ByteArray, hooksDirectory: File): HookInstallOutcome {
        val hook = hooksDirectory.resolve(HOOK_NAME)
        val record = hooksDirectory.resolve(RECORD_NAME)
        if (Files.isSymbolicLink(hook.toPath())) {
            return HookInstallOutcome.KEPT_SYMLINK
        }
        if (!hook.exists()) {
            write(source, hook, record)
            return HookInstallOutcome.INSTALLED
        }

        val current = hook.readBytes()
        if (current.contentEquals(source)) {
            record.writeText(sha256(source))
            return HookInstallOutcome.UNCHANGED
        }
        val recorded = record.takeIf(File::isFile)?.readText()?.trim()
        if (recorded == sha256(current)) {
            write(source, hook, record)
            return HookInstallOutcome.UPDATED
        }
        return HookInstallOutcome.KEPT_UNMANAGED
    }

    private fun write(source: ByteArray, hook: File, record: File) {
        hook.parentFile.mkdirs()
        hook.writeBytes(source)
        makeExecutable(hook)
        record.writeText(sha256(source))
    }

    private fun makeExecutable(file: File) {
        val path = file.toPath()
        if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        } else {
            file.setExecutable(true, false)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
