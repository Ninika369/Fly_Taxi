package com.george.securitysupport.session.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionValidationResultTest {

    @Test
    void shouldRepresentValidSession() {
        SessionValidationResult result = SessionValidationResult.valid();

        assertTrue(result.isValid());
        assertEquals(SessionValidationStatus.VALID, result.getStatus());
        assertEquals(SessionValidationResult.valid(), result);
        assertEquals(SessionValidationResult.valid().hashCode(), result.hashCode());
    }

    @Test
    void shouldRepresentFailureStatus() {
        for (SessionValidationStatus status : new SessionValidationStatus[] {
                SessionValidationStatus.SESSION_NOT_FOUND,
                SessionValidationStatus.SESSION_REVOKED,
                SessionValidationStatus.SESSION_TOKEN_MISMATCH,
                SessionValidationStatus.SESSION_STORE_UNAVAILABLE
        }) {
            SessionValidationResult result = SessionValidationResult.failure(status);

            assertFalse(result.isValid());
            assertEquals(status, result.getStatus());
            assertEquals(SessionValidationResult.failure(status), result);
            assertEquals(SessionValidationResult.failure(status).hashCode(), result.hashCode());
            assertNotEquals(SessionValidationResult.valid(), result);
        }
    }

    @Test
    void shouldRejectInvalidFailureFactories() {
        assertThrows(NullPointerException.class, () -> SessionValidationResult.failure(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionValidationResult.failure(SessionValidationStatus.VALID));
    }
}
