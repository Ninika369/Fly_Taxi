package com.george.securitysupport.core.model;

import com.george.securitysupport.core.testfixture.SecurityTestFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestPrincipalTest {

    @Test
    void shouldExposeTypedPrincipalFields() {
        RequestPrincipal principal = SecurityTestFixtures.passengerPrincipal();
        RequestPrincipal equivalent = new RequestPrincipal(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                "flytaxi-auth");

        assertEquals("passenger-1001", principal.getSubjectId());
        assertEquals(Role.PASSENGER, principal.getRole());
        assertEquals(TokenType.ACCESS, principal.getTokenType());
        assertEquals("token-passenger-access-1", principal.getTokenId());
        assertEquals("flytaxi-auth", principal.getIssuer());
        assertEquals(equivalent, principal);
        assertEquals(equivalent.hashCode(), principal.hashCode());
    }

    @Test
    void shouldRejectRefreshTokenPrincipal() {
        assertThrows(IllegalArgumentException.class, () -> new RequestPrincipal(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.REFRESH,
                "token-refresh-1",
                "flytaxi-auth"));
    }

    @Test
    void shouldRemainFrameworkNeutralByDeclaredFieldTypes() {
        for (Field field : RequestPrincipal.class.getDeclaredFields()) {
            String fieldType = field.getType().getName();
            assertFalse(isForbiddenFrameworkType(fieldType),
                    () -> field.getName() + " uses forbidden type " + fieldType);
        }
    }

    private static boolean isForbiddenFrameworkType(String fieldType) {
        return fieldType.startsWith("javax.servlet")
                || fieldType.startsWith("jakarta.servlet")
                || fieldType.startsWith("org.springframework")
                || fieldType.startsWith("org.springframework.security")
                || fieldType.startsWith("org.springframework.data.redis")
                || fieldType.equals("java.lang.ThreadLocal");
    }
}
