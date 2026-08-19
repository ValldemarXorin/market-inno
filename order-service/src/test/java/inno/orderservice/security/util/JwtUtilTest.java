package inno.orderservice.security.util;

import inno.orderservice.security.config.JwtValidationProperties;
import inno.orderservice.security.entity.Role;
import inno.orderservice.security.exception.InvalidTokenException;
import inno.orderservice.security.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "integration-test-secret-at-least-32-bytes-long!!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(new JwtValidationProperties(SECRET));
    }

    @Test
    void shouldParseAndValidateValidToken() {
        UUID userId = UUID.randomUUID();

        Claims claims = jwtUtil.parseAndValidate(token(userId, Role.ADMIN, System.currentTimeMillis() + 3600_000));

        assertEquals(userId, jwtUtil.extractUserId(claims));
        assertEquals(Role.ADMIN, jwtUtil.extractRole(claims));
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = token(UUID.randomUUID(), Role.USER, System.currentTimeMillis() - 1000);

        assertThrows(TokenExpiredException.class, () -> jwtUtil.parseAndValidate(token));
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThrows(InvalidTokenException.class, () -> jwtUtil.parseAndValidate("not-a-jwt"));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.USER.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret-1".getBytes()))
                .compact();

        assertThrows(InvalidTokenException.class, () -> jwtUtil.parseAndValidate(token));
    }

    private String token(UUID userId, Role role, long expirationMillis) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(expirationMillis))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}