package com.george.securitysupport.session.web;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BearerTokenExtractorTest {

    private final BearerTokenExtractor extractor = new BearerTokenExtractor();

    @Test
    void shouldExtractBearerTokenCaseInsensitively() {
        assertEquals(Optional.of("synthetic-token"), extractor.extract("Bearer synthetic-token"));
        assertEquals(Optional.of("synthetic-token"), extractor.extract(" bearer   synthetic-token "));
    }

    @Test
    void shouldReturnEmptyForMissingOrWrongScheme() {
        assertFalse(extractor.extract(null).isPresent());
        assertFalse(extractor.extract("").isPresent());
        assertFalse(extractor.extract("   ").isPresent());
        assertFalse(extractor.extract("Basic synthetic-token").isPresent());
        assertFalse(extractor.extract("Bearer").isPresent());
    }

    @Test
    void shouldReturnEmptyForBlankOrWhitespaceContainingToken() {
        assertFalse(extractor.extract("Bearer     ").isPresent());
        assertFalse(extractor.extract("Bearer token with space").isPresent());
        assertFalse(extractor.extract("Bearer token\twith-tab").isPresent());
    }
}
