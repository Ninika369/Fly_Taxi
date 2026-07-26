package com.george.securitysupport.core.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityFailureCodeTest {

    @Test
    void shouldClassifyTokenFailuresAsAuthentication() {
        SecurityFailureCode[] authenticationFailures = {
                SecurityFailureCode.MALFORMED_TOKEN,
                SecurityFailureCode.INVALID_SIGNATURE,
                SecurityFailureCode.TOKEN_EXPIRED,
                SecurityFailureCode.INVALID_TOKEN_TYPE,
                SecurityFailureCode.INVALID_ISSUER,
                SecurityFailureCode.INVALID_AUDIENCE,
                SecurityFailureCode.MISSING_REQUIRED_CLAIM
        };

        for (SecurityFailureCode failureCode : authenticationFailures) {
            assertEquals(SecurityFailureCategory.AUTHENTICATION, failureCode.getCategory());
        }
    }

    @Test
    void shouldClassifyRoleFailureAsAuthorization() {
        assertEquals(SecurityFailureCategory.AUTHORIZATION, SecurityFailureCode.ROLE_NOT_ALLOWED.getCategory());
    }
}
