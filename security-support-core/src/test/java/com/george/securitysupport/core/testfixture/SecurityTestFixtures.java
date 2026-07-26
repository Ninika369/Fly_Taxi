package com.george.securitysupport.core.testfixture;

import com.george.securitysupport.core.model.RequestPrincipal;
import com.george.securitysupport.core.model.Role;
import com.george.securitysupport.core.model.TokenClaims;
import com.george.securitysupport.core.model.TokenType;
import com.george.securitysupport.core.verification.TokenVerificationPolicy;

import java.time.Instant;
import java.util.EnumSet;

public final class SecurityTestFixtures {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:30:00Z");
    private static final String ISSUER = "flytaxi-auth";

    private SecurityTestFixtures() {
    }

    public static TokenClaims passengerAccessClaims() {
        return new TokenClaims(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                ISSUER,
                "flytaxi-passenger-api",
                ISSUED_AT,
                EXPIRES_AT);
    }

    public static TokenClaims serviceClaims() {
        return new TokenClaims(
                "service-order",
                Role.SERVICE,
                TokenType.SERVICE,
                "token-service-order-1",
                ISSUER,
                "flytaxi-internal-services",
                ISSUED_AT,
                EXPIRES_AT);
    }

    public static RequestPrincipal passengerPrincipal() {
        return new RequestPrincipal(
                "passenger-1001",
                Role.PASSENGER,
                TokenType.ACCESS,
                "token-passenger-access-1",
                ISSUER);
    }

    public static TokenVerificationPolicy passengerAccessPolicy() {
        return new TokenVerificationPolicy(
                TokenType.ACCESS,
                ISSUER,
                "flytaxi-passenger-api",
                EnumSet.of(Role.PASSENGER));
    }

    public static TokenVerificationPolicy servicePolicy() {
        return new TokenVerificationPolicy(
                TokenType.SERVICE,
                ISSUER,
                "flytaxi-internal-services",
                EnumSet.of(Role.SERVICE));
    }
}
