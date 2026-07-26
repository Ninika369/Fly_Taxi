package com.george.securitysupport.core.model;

import com.george.securitysupport.core.testfixture.SecurityTestFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenClaimsTest {

    @Test
    void shouldExposeTypedSanitizedClaims() {
        TokenClaims claims = SecurityTestFixtures.passengerAccessClaims();
        TokenClaims equivalent = new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "flytaxi-auth",
                "flytaxi-passenger-api",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:30:00Z"));

        assertEquals("passenger-1001", claims.getSubjectId());
        assertEquals(Role.PASSENGER, claims.getRole());
        assertEquals(TokenType.ACCESS, claims.getTokenType());
        assertEquals("token-passenger-access-1", claims.getTokenId());
        assertEquals("flytaxi-auth", claims.getIssuer());
        assertEquals("flytaxi-passenger-api", claims.getAudience());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), claims.getIssuedAt());
        assertEquals(Instant.parse("2026-01-01T00:30:00Z"), claims.getExpiresAt());
        assertEquals(equivalent, claims);
        assertEquals(equivalent.hashCode(), claims.hashCode());
        assertNoRawClaimsMap();
    }

    @Test
    void shouldRejectBlankRequiredClaims() {
        assertThrows(IllegalArgumentException.class, () -> new TokenClaims(
                " ",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "flytaxi-auth",
                "flytaxi-passenger-api",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:30:00Z")));

        assertThrows(IllegalArgumentException.class, () -> new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "",
                "flytaxi-passenger-api",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:30:00Z")));
    }

    @Test
    void shouldRejectNonIncreasingExpiry() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "flytaxi-auth",
                "flytaxi-passenger-api",
                issuedAt,
                issuedAt));

        assertThrows(IllegalArgumentException.class, () -> new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "flytaxi-auth",
                "flytaxi-passenger-api",
                issuedAt,
                issuedAt.minusSeconds(1)));
    }

    private static void assertNoRawClaimsMap() {
        for (Field field : TokenClaims.class.getDeclaredFields()) {
            assertFalse(Map.class.isAssignableFrom(field.getType()),
                    () -> field.getName() + " exposes raw claims map storage");
        }
        for (Method method : TokenClaims.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertFalse(Map.class.isAssignableFrom(method.getReturnType()),
                        () -> method.getName() + " exposes a raw claims map");
            }
        }
    }
}
