package com.george.securitysupport.session.web;

import java.util.Objects;

public final class SecurityHttpError {

    private final int statusCode;
    private final String code;
    private final String message;

    SecurityHttpError(
            int statusCode,
            String code,
            String message) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599");
        }
        this.statusCode = statusCode;
        this.code = requireText(code, "code");
        this.message = requireText(message, "message");
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SecurityHttpError)) {
            return false;
        }
        SecurityHttpError that = (SecurityHttpError) other;
        return statusCode == that.statusCode
                && Objects.equals(code, that.code)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, code, message);
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
}
