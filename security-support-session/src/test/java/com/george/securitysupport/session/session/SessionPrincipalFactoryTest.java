package com.george.securitysupport.session.session;

import com.george.securitysupport.core.error.SecurityFailureCode;
import com.george.securitysupport.core.model.RequestPrincipal;
import com.george.securitysupport.core.model.Role;
import com.george.securitysupport.core.model.TokenClaims;
import com.george.securitysupport.core.model.TokenType;
import com.george.securitysupport.core.testfixture.SecurityTestFixtures;
import com.george.securitysupport.core.verification.TokenVerificationResult;
import com.george.securitysupport.core.verification.ValidationRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionPrincipalFactoryTest {

    private final SessionPrincipalFactory factory = new SessionPrincipalFactory();

    @Test
    void shouldCreatePrincipalAfterVerifiedAccessAndValidSession() {
        TokenClaims claims = SecurityTestFixtures.passengerAccessClaims();
        TokenVerificationResult verificationResult = TokenVerificationResult.verified(
                claims,
                ValidationRequirement.SESSION_VALIDATION_REQUIRED);

        RequestPrincipal principal = factory.create(
                verificationResult,
                SessionValidationResult.valid());

        assertEquals(claims.getSubjectId(), principal.getSubjectId());
        assertEquals(claims.getRole(), principal.getRole());
        assertEquals(TokenType.ACCESS, principal.getTokenType());
        assertEquals(claims.getTokenId(), principal.getTokenId());
        assertEquals(claims.getIssuer(), principal.getIssuer());
    }

    @Test
    void shouldRejectInvalidSession() {
        TokenVerificationResult verificationResult = TokenVerificationResult.verified(
                SecurityTestFixtures.passengerAccessClaims(),
                ValidationRequirement.SESSION_VALIDATION_REQUIRED);

        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        verificationResult,
                        SessionValidationResult.failure(SessionValidationStatus.SESSION_REVOKED)));
        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        verificationResult,
                        SessionValidationResult.failure(SessionValidationStatus.SESSION_TOKEN_MISMATCH)));
    }

    @Test
    void shouldRejectRejectedOrNonAccessVerification() {
        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        TokenVerificationResult.rejected(SecurityFailureCode.MALFORMED_TOKEN),
                        SessionValidationResult.valid()));

        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        TokenVerificationResult.verified(
                                SecurityTestFixtures.serviceClaims(),
                                ValidationRequirement.NONE),
                        SessionValidationResult.valid()));

        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        TokenVerificationResult.verified(
                                refreshClaims(),
                                ValidationRequirement.SESSION_VALIDATION_REQUIRED),
                        SessionValidationResult.valid()));
    }

    private static TokenClaims refreshClaims() {
        return new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.REFRESH,
                "token-passenger-refresh-1",
                "flytaxi-auth",
                "flytaxi-passenger-api",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"));
    }
}
