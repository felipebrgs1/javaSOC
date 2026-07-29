package com.felipeb.discordclone.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
            "test-secret-with-at-least-32-bytes-of-entropy-aaaaaaaa", 60_000);

    @Test
    void issueAndParseRoundTrip() {
        String token = jwt.issue(42L, "alice");

        JwtService.AuthenticatedUser parsed = jwt.parse(token);

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("alice");
    }

    @Test
    void differentTokensForDifferentPayloads() {
        String t1 = jwt.issue(1L, "alice");
        String t2 = jwt.issue(2L, "bob");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.issue(42L, "alice");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwt.parse(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService("other-secret-with-at-least-32-bytes-of-entropy-bbbbbb", 60_000);
        String foreignToken = other.issue(7L, "mallory");

        assertThatThrownBy(() -> jwt.parse(foreignToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
