package com.george.securitysupport.core.error;

public enum SecurityFailureCode {
    MALFORMED_TOKEN(SecurityFailureCategory.AUTHENTICATION),
    INVALID_SIGNATURE(SecurityFailureCategory.AUTHENTICATION),
    TOKEN_EXPIRED(SecurityFailureCategory.AUTHENTICATION),
    INVALID_TOKEN_TYPE(SecurityFailureCategory.AUTHENTICATION),
    INVALID_ISSUER(SecurityFailureCategory.AUTHENTICATION),
    INVALID_AUDIENCE(SecurityFailureCategory.AUTHENTICATION),
    MISSING_REQUIRED_CLAIM(SecurityFailureCategory.AUTHENTICATION),
    ROLE_NOT_ALLOWED(SecurityFailureCategory.AUTHORIZATION);

    private final SecurityFailureCategory category;

    SecurityFailureCode(SecurityFailureCategory category) {
        this.category = category;
    }

    public SecurityFailureCategory getCategory() {
        return category;
    }
}
