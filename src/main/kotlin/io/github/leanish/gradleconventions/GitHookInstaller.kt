/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

/** What [GitHookInstaller.install] did with the destination hook. */
internal enum class HookInstallOutcome {
    /** No hook was there; the source was written. */
    INSTALLED,

    /** The hook was one this installer wrote (per its record, or a previously bundled hook), unedited; it was replaced. */
    UPDATED,

    /** The hook already had the source's bytes (made executable if it wasn't). */
    UNCHANGED,

    /** The hook wasn't written by this installer, or was edited since; it was left as is. */
    KEPT_UNMANAGED,

    /** The hook is a symbolic link; it was left as is. */
    KEPT_SYMLINK,

    /** The hook isn't a regular file, or its record is a symbolic link or not a regular file; nothing was written. */
    KEPT_NOT_REGULAR,
}

/**
 * Installs the pre-commit hook without overwriting one the developer owns.
 *
 * Next to the hook it keeps a record of the SHA-256 of the bytes it last wrote. A hook it may replace is one that is
 * absent, already identical, or still matching that record; anything else (a hook from another tool, a hand-written
 * one, an installed hook edited locally, a symbolic link) is kept. Hooks installed before the record existed are
 * recognized by the exact bytes of the previously bundled hooks, but only while there is no record: a record that
 * disagrees means a local edit, whatever the bytes.
 */
internal object GitHookInstaller {
    const val HOOK_NAME = "pre-commit"

    /** Git only runs files named after hooks, so the record in the hooks directory is inert. */
    const val RECORD_NAME = "pre-commit.java-conventions.sha256"

    /** SHA-256 of every pre-commit hook bundled before the record existed (git history of git-hooks/pre-commit). */
    private val PREVIOUSLY_BUNDLED = setOf(
        "43efe0f1e11b29b0cf812e56034ad73523b2fb4211e893d282f157b465af8a7f", // 689378a, from 0.2.0
        "65cbd8732b3c551beb04986fa0524c5960fdab80eceadc361aa6d7e6d85bff09", // d511ef3
    )

    fun install(source: ByteArray, hooksDirectory: File): HookInstallOutcome {
        val hook = hooksDirectory.resolve(HOOK_NAME).toPath()
        val record = hooksDirectory.resolve(RECORD_NAME).toPath()
        if (Files.isSymbolicLink(hook)) {
            return HookInstallOutcome.KEPT_SYMLINK
        }
        // Checked before anything is read or written: writing through a linked record would truncate its target.
        if (!isAbsentOrRegularFile(hook) || !isAbsentOrRegularFile(record)) {
            return HookInstallOutcome.KEPT_NOT_REGULAR
        }
        if (Files.notExists(hook, NOFOLLOW_LINKS)) {
            write(source, hook, record)
            return HookInstallOutcome.INSTALLED
        }

        val current = Files.readAllBytes(hook)
        if (current.contentEquals(source)) {
            // Identical bytes don't make an active hook: Git skips one that isn't executable.
            makeExecutable(hook)
            Files.writeString(record, sha256(source))
            return HookInstallOutcome.UNCHANGED
        }
        val currentDigest = sha256(current)
        val managed = if (Files.exists(record, NOFOLLOW_LINKS)) {
            Files.readString(record).trim() == currentDigest
        } else {
            currentDigest in PREVIOUSLY_BUNDLED
        }
        if (managed) {
            write(source, hook, record)
            return HookInstallOutcome.UPDATED
        }
        return HookInstallOutcome.KEPT_UNMANAGED
    }

    private fun isAbsentOrRegularFile(path: Path): Boolean =
        Files.notExists(path, NOFOLLOW_LINKS) || Files.isRegularFile(path, NOFOLLOW_LINKS)

    private fun write(source: ByteArray, hook: Path, record: Path) {
        Files.createDirectories(hook.parent)
        Files.write(hook, source)
        makeExecutable(hook)
        Files.writeString(record, sha256(source))
    }

    private fun makeExecutable(path: Path) {
        if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        } else {
            check(path.toFile().setExecutable(true, false)) { "couldn't make $path executable" }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
