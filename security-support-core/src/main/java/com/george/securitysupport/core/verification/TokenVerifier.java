package com.george.securitysupport.core.verification;

public interface TokenVerifier {

    TokenVerificationResult verify(
            String token,
            TokenVerificationPolicy policy);
}
