package com.george.securitysupport.core.verification;

import com.george.securitysupport.core.error.SecurityFailureCode;
import com.george.securitysupport.core.model.TokenClaims;
import com.george.securitysupport.core.model.TokenType;

import java.util.Objects;

public final class TokenVerificationResult {

    private final boolean verified;
    private final TokenClaims claims;
    private final SecurityFailureCode failureCode;
    private final ValidationRequirement validationRequirement;

    private TokenVerificationResult(
            boolean verified,
            TokenClaims claims,
            SecurityFailureCode failureCode,
            ValidationRequirement validationRequirement) {
        this.verified = verified;
        this.claims = claims;
        this.failureCode = failureCode;
        this.validationRequirement = validationRequirement;
    }

    public static TokenVerificationResult verified(
            TokenClaims claims,
            ValidationRequirement validationRequirement) {
        TokenClaims verifiedClaims = Objects.requireNonNull(claims, "claims must not be null");
        ValidationRequirement requirement = Objects.requireNonNull(
                validationRequirement,
                "validationRequirement must not be null");
        validateRequirement(verifiedClaims.getTokenType(), requirement);
        return new TokenVerificationResult(true, verifiedClaims, null, requirement);
    }

    public static TokenVerificationResult rejected(
            SecurityFailureCode failureCode) {
        SecurityFailureCode rejectedCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
        return new TokenVerificationResult(false, null, rejectedCode, ValidationRequirement.NONE);
    }

    public boolean isVerified() {
        return verified;
    }

    public TokenClaims getClaims() {
        if (!verified) {
            throw new IllegalStateException("rejected results do not contain claims");
        }
        return claims;
    }

    public SecurityFailureCode getFailureCode() {
        if (verified) {
            throw new IllegalStateException("verified results do not contain a failure code");
        }
        return failureCode;
    }

    public ValidationRequirement getValidationRequirement() {
        return validationRequirement;
    }

    private static void validateRequirement(
            TokenType tokenType,
            ValidationRequirement validationRequirement) {
        if (tokenType == TokenType.SERVICE) {
            if (validationRequirement != ValidationRequirement.NONE) {
                throw new IllegalArgumentException("service tokens must not require session validation");
            }
            return;
        }
        if (validationRequirement != ValidationRequirement.SESSION_VALIDATION_REQUIRED) {
            throw new IllegalArgumentException("user tokens require session validation");
        }
    }
}
