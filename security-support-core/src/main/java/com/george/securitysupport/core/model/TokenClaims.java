package com.george.securitysupport.core.model;

import java.time.Instant;
import java.util.Objects;

public final class TokenClaims {

    private final String subjectId;
    private final Role role;
    private final TokenType tokenType;
    private final String tokenId;
    private final String issuer;
    private final String audience;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public TokenClaims(
            String subjectId,
            Role role,
            TokenType tokenType,
            String tokenId,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.tokenType = Objects.requireNonNull(tokenType, "tokenType must not be null");
        this.tokenId = requireText(tokenId, "tokenId");
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = requireExpiresAfterIssuedAt(expiresAt, this.issuedAt);
    }

    public String getSubjectId() {
        return subjectId;
    }

    public Role getRole() {
        return role;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getAudience() {
        return audience;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TokenClaims)) {
            return false;
        }
        TokenClaims that = (TokenClaims) other;
        return Objects.equals(subjectId, that.subjectId)
                && role == that.role
                && tokenType == that.tokenType
                && Objects.equals(tokenId, that.tokenId)
                && Objects.equals(issuer, that.issuer)
                && Objects.equals(audience, that.audience)
                && Objects.equals(issuedAt, that.issuedAt)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, role, tokenType, tokenId, issuer, audience, issuedAt, expiresAt);
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

    private static Instant requireExpiresAfterIssuedAt(Instant expiresAt, Instant issuedAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        return expiresAt;
    }
}
