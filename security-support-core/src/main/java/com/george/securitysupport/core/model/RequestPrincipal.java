package com.george.securitysupport.core.model;

import java.util.Objects;

public final class RequestPrincipal {

    private final String subjectId;
    private final Role role;
    private final TokenType tokenType;
    private final String tokenId;
    private final String issuer;

    public RequestPrincipal(
            String subjectId,
            Role role,
            TokenType tokenType,
            String tokenId,
            String issuer) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.tokenType = requireRequestTokenType(tokenType);
        this.tokenId = requireText(tokenId, "tokenId");
        this.issuer = requireText(issuer, "issuer");
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequestPrincipal)) {
            return false;
        }
        RequestPrincipal that = (RequestPrincipal) other;
        return Objects.equals(subjectId, that.subjectId)
                && role == that.role
                && tokenType == that.tokenType
                && Objects.equals(tokenId, that.tokenId)
                && Objects.equals(issuer, that.issuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, role, tokenType, tokenId, issuer);
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

    private static TokenType requireRequestTokenType(TokenType tokenType) {
        if (tokenType == null) {
            throw new IllegalArgumentException("tokenType must not be null");
        }
        if (tokenType == TokenType.REFRESH) {
            throw new IllegalArgumentException("refresh tokens cannot be request principals");
        }
        return tokenType;
    }
}
