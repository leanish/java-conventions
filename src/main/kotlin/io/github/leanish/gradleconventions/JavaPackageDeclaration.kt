/*
 * Copyright (c) 2026 Leandro Aguiar
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package io.github.leanish.gradleconventions

/**
 * Reads the package declaration at the top of a Java compilation unit, the way the compiler sees it: Unicode escapes
 * are translated first, comments and whitespace may appear anywhere between tokens, annotations may precede it (in
 * `package-info.java`, with string, text block and character literals inside), and identifiers are Java identifiers
 * (Unicode letters and `$` included). A declaration-shaped line inside a comment is not a declaration.
 *
 * Returns null when the file has no package declaration (the default package) or doesn't start like a Java file.
 */
internal object JavaPackageDeclaration {
    private val unicodeEscape = Regex("""(?<!\\)((?:\\\\)*)\\u+([0-9a-fA-F]{4})""")

    fun parse(source: String): String? {
        val text = unicodeEscape.replace(source) { match ->
            match.groupValues[1] + match.groupValues[2].toInt(16).toChar()
        }
        var index = skipIgnorable(text, 0)
        while (text.getOrNull(index) == '@') {
            index = skipAnnotation(text, index) ?: return null
            index = skipIgnorable(text, index)
        }
        if (!text.startsWith("package", index) || isIdentifierPart(text.getOrNull(index + "package".length))) {
            return null
        }
        index = skipIgnorable(text, index + "package".length)
        val segments = mutableListOf<String>()
        while (true) {
            val end = identifierEnd(text, index)
            if (end == index) {
                return null
            }
            segments += text.substring(index, end)
            index = skipIgnorable(text, end)
            when (text.getOrNull(index)) {
                '.' -> index = skipIgnorable(text, index + 1)
                ';' -> return segments.joinToString(".")
                else -> return null
            }
        }
    }

    /**
     * Skips whitespace and comments from [start]; an unterminated block comment runs to the end. A line comment ends at
     * any Java line terminator: LF, CR or CRLF (also when written as a Unicode escape, translated before this).
     */
    private fun skipIgnorable(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            index = when {
                text[index].isWhitespace() -> index + 1
                text.startsWith("//", index) -> lineEnd(text, index)
                text.startsWith("/*", index) -> text.indexOf("*/", index + 2).let { if (it < 0) text.length else it + 2 }
                else -> return index
            }
        }
        return index
    }

    /** The index after the line terminator that ends the line at [start], or the end of [text]. */
    private fun lineEnd(text: String, start: Int): Int {
        val terminator = text.indexOfAny(charArrayOf('\n', '\r'), start)
        return if (terminator < 0) text.length else terminator + 1
    }

    /** Skips `@Name`, `@a.b.Name` or `@Name(...)` from the `@` at [start]; null if it isn't one. */
    private fun skipAnnotation(text: String, start: Int): Int? {
        var index = skipIgnorable(text, start + 1)
        while (true) {
            val end = identifierEnd(text, index)
            if (end == index) {
                return null
            }
            index = skipIgnorable(text, end)
            if (text.getOrNull(index) != '.') {
                break
            }
            index = skipIgnorable(text, index + 1)
        }
        return if (text.getOrNull(index) == '(') skipParenthesized(text, index) else index
    }

    /** Skips from the `(` at [start] past its matching `)`, ignoring parentheses in literals and comments. */
    private fun skipParenthesized(text: String, start: Int): Int? {
        var depth = 0
        var index = start
        while (index < text.length) {
            when {
                text.startsWith("//", index) || text.startsWith("/*", index) -> {
                    index = skipIgnorable(text, index)
                    continue
                }
                text.startsWith("\"\"\"", index) -> index = literalEnd(text, index + 3, "\"\"\"") ?: return null
                text[index] == '"' -> index = literalEnd(text, index + 1, "\"") ?: return null
                text[index] == '\'' -> index = literalEnd(text, index + 1, "'") ?: return null
                text[index] == '(' -> {
                    depth++
                    index++
                }
                text[index] == ')' -> {
                    depth--
                    index++
                    if (depth == 0) {
                        return index
                    }
                }
                else -> index++
            }
        }
        return null
    }

    /** The index after the unescaped [delimiter] that closes a literal whose content starts at [start]. */
    private fun literalEnd(text: String, start: Int, delimiter: String): Int? {
        var index = start
        while (index < text.length) {
            when {
                text[index] == '\\' -> index += 2
                text.startsWith(delimiter, index) -> return index + delimiter.length
                else -> index++
            }
        }
        return null
    }

    private fun identifierEnd(text: String, start: Int): Int {
        if (start >= text.length || !Character.isJavaIdentifierStart(text.codePointAt(start))) {
            return start
        }
        var index = start + Character.charCount(text.codePointAt(start))
        while (index < text.length && Character.isJavaIdentifierPart(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        return index
    }

    private fun isIdentifierPart(char: Char?): Boolean = char != null && Character.isJavaIdentifierPart(char)
}
