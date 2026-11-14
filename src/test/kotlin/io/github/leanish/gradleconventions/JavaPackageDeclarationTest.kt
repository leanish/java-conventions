package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaPackageDeclarationTest {
    @Test
    fun readsAPlainDeclaration() {
        assertThat(JavaPackageDeclaration.parse("package com.example.app;\npublic class A {}")).isEqualTo("com.example.app")
    }

    @Test
    fun ignoresADeclarationInsideABlockComment() {
        val source = """
            /*
             * package stale.example;
             */
            package com.example;
        """.trimIndent()

        assertThat(JavaPackageDeclaration.parse(source)).isEqualTo("com.example")
    }

    @Test
    fun ignoresADeclarationInsideALineComment() {
        assertThat(JavaPackageDeclaration.parse("// package stale.example;\npackage com.example;")).isEqualTo("com.example")
    }

    @Test
    fun acceptsSameLineAndInterTokenComments() {
        assertThat(JavaPackageDeclaration.parse("package com /* c */ . example // trailing\n ;")).isEqualTo("com.example")
        assertThat(JavaPackageDeclaration.parse("package com.example; // trailing")).isEqualTo("com.example")
    }

    @Test
    fun acceptsUnicodeIdentifiersAndDollar() {
        assertThat(JavaPackageDeclaration.parse("package com.exämple.ünits\$1;")).isEqualTo("com.exämple.ünits\$1")
    }

    @Test
    fun translatesUnicodeEscapesFirst() {
        assertThat(JavaPackageDeclaration.parse("package com.\\u0065xample;")).isEqualTo("com.example")
        assertThat(JavaPackageDeclaration.parse("package com.\\\\u0065xample;")).isNull()
    }

    @Test
    fun skipsAnnotationsWithLiteralsThatLookLikeComments() {
        val source = """
            @Deprecated(since = "/* not a comment")
            @SuppressWarnings({"unused", ")"})
            @org.jspecify.annotations.NullMarked
            @Custom(value = '"', text = ""${'"'}
                ) package not.this;
                ""${'"'})
            package com.example;
        """.trimIndent()

        assertThat(JavaPackageDeclaration.parse(source)).isEqualTo("com.example")
    }

    @Test
    fun returnsNullWithoutADeclaration() {
        assertThat(JavaPackageDeclaration.parse("import java.util.List;\npublic class A {}")).isNull()
        assertThat(JavaPackageDeclaration.parse("public class A { String s = \"package x;\"; }")).isNull()
        assertThat(JavaPackageDeclaration.parse("packages.x;")).isNull()
        assertThat(JavaPackageDeclaration.parse("package ;")).isNull()
    }
}
