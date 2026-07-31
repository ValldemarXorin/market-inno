package inno.authservice.util;

import inno.authservice.exception.custom_exception.InvalidTokenException;

public final class TokenExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    private TokenExtractor() {}

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Authorization header must start with 'Bearer '");
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
