package com.george.securitysupport.core.verification;

import com.george.securitysupport.core.error.SecurityFailureCategory;
import com.george.securitysupport.core.error.SecurityFailureCode;
import com.george.securitysupport.core.model.Role;
import com.george.securitysupport.core.testfixture.SecurityTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenVerificationResultTest {

    @Test
    void shouldRequireSessionValidationForAccessTokens() {
        TokenVerificationResult result = TokenVerificationResult.verified(
                SecurityTestFixtures.passengerAccessClaims(),
                ValidationRequirement.SESSION_VALIDATION_REQUIRED);
        TokenVerificationPolicy policy = SecurityTestFixtures.passengerAccessPolicy();

        assertTrue(result.isVerified());
        assertEquals(ValidationRequirement.SESSION_VALIDATION_REQUIRED, result.getValidationRequirement());
        assertEquals("passenger-1001", result.getClaims().getSubjectId());
        assertThrows(IllegalStateException.class, result::getFailureCode);
        assertTrue(policy.getAllowedRoles().contains(Role.PASSENGER));
        assertThrows(UnsupportedOperationException.class, () -> policy.getAllowedRoles().add(Role.ADMIN));
    }

    @Test
    void shouldRequireNoSessionValidationForServiceTokens() {
        TokenVerificationResult result = TokenVerificationResult.verified(
                SecurityTestFixtures.serviceClaims(),
                ValidationRequirement.NONE);
        TokenVerificationPolicy policy = SecurityTestFixtures.servicePolicy();

        assertTrue(result.isVerified());
        assertEquals(ValidationRequirement.NONE, result.getValidationRequirement());
        assertEquals("service-order", result.getClaims().getSubjectId());
        assertEquals(Role.SERVICE, policy.getAllowedRoles().iterator().next());
    }

    @Test
    void shouldRejectValidationRequirementThatContradictsTokenType() {
        assertThrows(IllegalArgumentException.class, () -> TokenVerificationResult.verified(
                SecurityTestFixtures.passengerAccessClaims(),
                ValidationRequirement.NONE));

        assertThrows(IllegalArgumentException.class, () -> TokenVerificationResult.verified(
                SecurityTestFixtures.serviceClaims(),
                ValidationRequirement.SESSION_VALIDATION_REQUIRED));
    }

    @Test
    void shouldExposeRejectedFailureWithoutClaims() {
        TokenVerificationResult result = TokenVerificationResult.rejected(SecurityFailureCode.TOKEN_EXPIRED);

        assertFalse(result.isVerified());
        assertEquals(ValidationRequirement.NONE, result.getValidationRequirement());
        assertSame(SecurityFailureCode.TOKEN_EXPIRED, result.getFailureCode());
        assertEquals(SecurityFailureCategory.AUTHENTICATION, result.getFailureCode().getCategory());
        assertThrows(IllegalStateException.class, result::getClaims);
    }
}
