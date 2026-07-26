package com.george.securitysupport.session.web;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BearerTokenExtractor {

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)^Bearer\\s+(.+)$");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile(".*\\s+.*");

    public Optional<String> extract(
            String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }

        String trimmedHeader = authorizationHeader.trim();
        if (trimmedHeader.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = BEARER_PATTERN.matcher(trimmedHeader);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String token = matcher.group(1).trim();
        if (token.isEmpty() || WHITESPACE_PATTERN.matcher(token).matches()) {
            return Optional.empty();
        }

        return Optional.of(token);
    }
}
