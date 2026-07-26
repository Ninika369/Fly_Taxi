package com.george.securitysupport.session.session;

import com.george.securitysupport.core.model.RequestPrincipal;
import com.george.securitysupport.core.model.TokenClaims;
import com.george.securitysupport.core.model.TokenType;
import com.george.securitysupport.core.verification.TokenVerificationResult;
import com.george.securitysupport.core.verification.ValidationRequirement;

import java.util.Objects;

public final class SessionPrincipalFactory {

    public RequestPrincipal create(
            TokenVerificationResult verificationResult,
            SessionValidationResult sessionValidationResult) {
        TokenVerificationResult verifiedResult = Objects.requireNonNull(
                verificationResult,
                "verificationResult must not be null");
        SessionValidationResult validatedSession = Objects.requireNonNull(
                sessionValidationResult,
                "sessionValidationResult must not be null");

        if (!verifiedResult.isVerified()) {
            throw new IllegalStateException("only verified token results can create request principals");
        }
        if (verifiedResult.getValidationRequirement() != ValidationRequirement.SESSION_VALIDATION_REQUIRED) {
            throw new IllegalStateException("request principals require session validation");
        }
        if (!validatedSession.isValid()) {
            throw new IllegalStateException("request principals require a valid session");
        }

        TokenClaims claims = verifiedResult.getClaims();
        if (claims.getTokenType() != TokenType.ACCESS) {
            throw new IllegalStateException("request principals require access token claims");
        }

        return new RequestPrincipal(
                claims.getSubjectId(),
                claims.getRole(),
                claims.getTokenType(),
                claims.getTokenId(),
                claims.getIssuer());
    }
}
