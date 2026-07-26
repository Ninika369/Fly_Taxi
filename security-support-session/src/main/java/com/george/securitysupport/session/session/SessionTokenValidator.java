package com.george.securitysupport.session.session;

import com.george.securitysupport.core.model.TokenClaims;

public interface SessionTokenValidator {

    SessionValidationResult validate(
            TokenClaims claims,
            String rawToken);
}
