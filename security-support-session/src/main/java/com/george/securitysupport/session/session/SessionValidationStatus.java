package com.george.securitysupport.session.session;

public enum SessionValidationStatus {
    VALID,
    SESSION_NOT_FOUND,
    SESSION_REVOKED,
    SESSION_TOKEN_MISMATCH,
    SESSION_STORE_UNAVAILABLE
}
