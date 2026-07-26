package com.george.securitysupport.session.session;

import java.util.Objects;

public final class SessionValidationResult {

    private final SessionValidationStatus status;

    private SessionValidationResult(SessionValidationStatus status) {
        this.status = status;
    }

    public static SessionValidationResult valid() {
        return new SessionValidationResult(SessionValidationStatus.VALID);
    }

    public static SessionValidationResult failure(
            SessionValidationStatus status) {
        SessionValidationStatus failureStatus = Objects.requireNonNull(status, "status must not be null");
        if (failureStatus == SessionValidationStatus.VALID) {
            throw new IllegalArgumentException("failure status must not be VALID");
        }
        return new SessionValidationResult(failureStatus);
    }

    public boolean isValid() {
        return status == SessionValidationStatus.VALID;
    }

    public SessionValidationStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionValidationResult)) {
            return false;
        }
        SessionValidationResult that = (SessionValidationResult) other;
        return status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }
}
