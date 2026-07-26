package com.george.securitysupport.core.verification;

import com.george.securitysupport.core.model.Role;
import com.george.securitysupport.core.model.TokenType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class TokenVerificationPolicy {

    private final TokenType expectedTokenType;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Set<Role> allowedRoles;

    public TokenVerificationPolicy(
            TokenType expectedTokenType,
            String expectedIssuer,
            String expectedAudience,
            Set<Role> allowedRoles) {
        this.expectedTokenType = Objects.requireNonNull(expectedTokenType, "expectedTokenType must not be null");
        this.expectedIssuer = requireText(expectedIssuer, "expectedIssuer");
        this.expectedAudience = requireText(expectedAudience, "expectedAudience");
        this.allowedRoles = copyAllowedRoles(allowedRoles);
    }

    public TokenType getExpectedTokenType() {
        return expectedTokenType;
    }

    public String getExpectedIssuer() {
        return expectedIssuer;
    }

    public String getExpectedAudience() {
        return expectedAudience;
    }

    public Set<Role> getAllowedRoles() {
        return allowedRoles;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TokenVerificationPolicy)) {
            return false;
        }
        TokenVerificationPolicy that = (TokenVerificationPolicy) other;
        return expectedTokenType == that.expectedTokenType
                && Objects.equals(expectedIssuer, that.expectedIssuer)
                && Objects.equals(expectedAudience, that.expectedAudience)
                && Objects.equals(allowedRoles, that.allowedRoles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedTokenType, expectedIssuer, expectedAudience, allowedRoles);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Set<Role> copyAllowedRoles(Set<Role> allowedRoles) {
        if (allowedRoles == null) {
            throw new IllegalArgumentException("allowedRoles must not be null");
        }
        if (allowedRoles.isEmpty()) {
            throw new IllegalArgumentException("allowedRoles must not be empty");
        }
        for (Role role : allowedRoles) {
            if (role == null) {
                throw new IllegalArgumentException("allowedRoles must not contain null");
            }
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(allowedRoles));
    }
}
