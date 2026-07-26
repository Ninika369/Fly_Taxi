package com.george.securitysupport.session.web;

import com.george.securitysupport.core.error.SecurityFailureCode;
import com.george.securitysupport.session.session.SessionValidationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityHttpErrorMapperTest {

    private final SecurityHttpErrorMapper mapper = new SecurityHttpErrorMapper();

    @Test
    void shouldMapCoreAuthenticationAndAuthorizationFailures() {
        SecurityHttpError authenticationError = mapper.fromCoreFailure(SecurityFailureCode.INVALID_SIGNATURE);
        SecurityHttpError authorizationError = mapper.fromCoreFailure(SecurityFailureCode.ROLE_NOT_ALLOWED);

        assertEquals(401, authenticationError.getStatusCode());
        assertEquals("INVALID_SIGNATURE", authenticationError.getCode());
        assertEquals("Authentication failed", authenticationError.getMessage());
        assertEquals(403, authorizationError.getStatusCode());
        assertEquals("ROLE_NOT_ALLOWED", authorizationError.getCode());
        assertEquals("Access denied", authorizationError.getMessage());
    }

    @Test
    void shouldMapSessionFailuresAndStoreOutage() {
        for (SessionValidationStatus status : new SessionValidationStatus[] {
                SessionValidationStatus.SESSION_NOT_FOUND,
                SessionValidationStatus.SESSION_REVOKED,
                SessionValidationStatus.SESSION_TOKEN_MISMATCH
        }) {
            SecurityHttpError error = mapper.fromSessionStatus(status);

            assertEquals(401, error.getStatusCode());
            assertEquals(status.name(), error.getCode());
            assertEquals("Authentication failed", error.getMessage());
        }

        SecurityHttpError outage = mapper.fromSessionStatus(SessionValidationStatus.SESSION_STORE_UNAVAILABLE);

        assertEquals(503, outage.getStatusCode());
        assertEquals("SESSION_STORE_UNAVAILABLE", outage.getCode());
        assertEquals("Session service unavailable", outage.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.fromSessionStatus(SessionValidationStatus.VALID));
    }

    @Test
    void shouldMapMissingBearerWithoutSensitiveData() {
        SecurityHttpError error = mapper.missingBearerCredential();
        String syntheticToken = "synthetic-token-secret";
        String syntheticSubject = "passenger-1001";

        assertEquals(401, error.getStatusCode());
        assertEquals("MISSING_BEARER_TOKEN", error.getCode());
        assertEquals("Authentication required", error.getMessage());
        assertFalse(error.getCode().contains(syntheticToken));
        assertFalse(error.getCode().contains(syntheticSubject));
        assertFalse(error.getMessage().contains(syntheticToken));
        assertFalse(error.getMessage().contains(syntheticSubject));
    }
}
