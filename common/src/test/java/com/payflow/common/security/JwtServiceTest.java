package com.payflow.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void generatedTokenParsesBackToSameUser() {
        AuthUser user = new AuthUser(7L, "isha@example.com", Role.CUSTOMER);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.parse(token)).contains(user);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-of-32-bytes", 60);
        String foreignToken = other.generateToken(new AuthUser(1L, "x@example.com", Role.ADMIN));

        assertThat(jwtService.parse(foreignToken)).isEmpty();
    }

    @Test
    void tokenWithRoleEditedToAdminIsRejected() {
        // An attacker decodes the payload, changes CUSTOMER to ADMIN and keeps the old signature.
        String token = jwtService.generateToken(new AuthUser(1L, "x@example.com", Role.CUSTOMER));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("CUSTOMER", "ADMIN").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(payload).contains("CUSTOMER");
        assertThat(jwtService.parse(forged)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService alreadyExpired = new JwtService(SECRET, -1);
        String token = alreadyExpired.generateToken(new AuthUser(1L, "x@example.com", Role.CUSTOMER));

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(jwtService.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
