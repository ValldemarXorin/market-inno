package inno.apigateway.security;

import inno.apigateway.TestTokens;
import inno.apigateway.security.config.JwtValidationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtReactiveDecoderTest {

    private final JwtReactiveDecoder decoder =
            new JwtReactiveDecoder(new JwtValidationProperties(TestTokens.SECRET));

    @Test
    void decodesValidToken() {
        UUID userId = UUID.randomUUID();
        String token = TestTokens.token(userId, "USER");

        Jwt jwt = decoder.decode(token).block();

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsExpiredToken() {
        String token = TestTokens.expiredToken(UUID.randomUUID(), "USER");

        assertThatThrownBy(() -> decoder.decode(token).block())
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> decoder.decode("not-a-jwt").block())
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-at-least-32-bytes-long!!".getBytes()))
                .compact();

        assertThatThrownBy(() -> decoder.decode(token).block())
                .isInstanceOf(BadJwtException.class);
    }
}
