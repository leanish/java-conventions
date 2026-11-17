package io.github.leanish.gradleconventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GithubOwnerResolverTest {
    @Test
    fun inferFromGroupReturnsNullForUnsupportedGroups() {
        assertThat(GithubOwnerResolver.inferFromGroup(null)).isNull()
        assertThat(GithubOwnerResolver.inferFromGroup("unspecified")).isNull()
        assertThat(GithubOwnerResolver.inferFromGroup("com.example")).isNull()
        assertThat(GithubOwnerResolver.inferFromGroup("io.github.")).isNull()
    }

    @Test
    fun inferFromGroupExtractsOwner() {
        assertThat(GithubOwnerResolver.inferFromGroup("io.github.acme")).isEqualTo("acme")
        assertThat(GithubOwnerResolver.inferFromGroup("io.github.acme.tooling")).isEqualTo("acme")
        assertThat(GithubOwnerResolver.inferFromGroup(" io.github.team_name.lib ")).isEqualTo("team_name")
    }
}
