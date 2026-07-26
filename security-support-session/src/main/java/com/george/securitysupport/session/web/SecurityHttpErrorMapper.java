package com.george.securitysupport.session.web;

import com.george.securitysupport.core.error.SecurityFailureCategory;
import com.george.securitysupport.core.error.SecurityFailureCode;
import com.george.securitysupport.session.session.SessionValidationStatus;

import java.util.Objects;

public final class SecurityHttpErrorMapper {

    public SecurityHttpError missingBearerCredential() {
        return new SecurityHttpError(
                401,
                "MISSING_BEARER_TOKEN",
                "Authentication required");
    }

    public SecurityHttpError fromCoreFailure(
            SecurityFailureCode failureCode) {
        SecurityFailureCode code = Objects.requireNonNull(failureCode, "failureCode must not be null");
        if (code.getCategory() == SecurityFailureCategory.AUTHORIZATION) {
            return new SecurityHttpError(
                    403,
                    code.name(),
                    "Access denied");
        }
        return new SecurityHttpError(
                401,
                code.name(),
                "Authentication failed");
    }

    public SecurityHttpError fromSessionStatus(
            SessionValidationStatus status) {
        SessionValidationStatus sessionStatus = Objects.requireNonNull(status, "status must not be null");
        if (sessionStatus == SessionValidationStatus.VALID) {
            throw new IllegalArgumentException("VALID session status cannot be mapped to an error");
        }
        if (sessionStatus == SessionValidationStatus.SESSION_STORE_UNAVAILABLE) {
            return new SecurityHttpError(
                    503,
                    sessionStatus.name(),
                    "Session service unavailable");
        }
        return new SecurityHttpError(
                401,
                sessionStatus.name(),
                "Authentication failed");
    }
}
