package inno.apigateway.security;

import inno.apigateway.security.config.JwtValidationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtReactiveDecoder implements ReactiveJwtDecoder {

    private final SecretKey signingKey;

    public JwtReactiveDecoder(JwtValidationProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes());
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return Mono.fromCallable(() -> parseAndValidate(token));
    }

    private Jwt parseAndValidate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return toJwt(token, jws);
        } catch (ExpiredJwtException e) {
            throw new BadJwtException("Access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadJwtException("Invalid access token");
        }
    }

    private Jwt toJwt(String token, Jws<Claims> jws) {
        Claims claims = jws.getPayload();
        Map<String, Object> headers = new HashMap<>(jws.getHeader());
        Map<String, Object> claimMap = new HashMap<>(claims);
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;
        return new Jwt(token, issuedAt, expiresAt, headers, claimMap);
    }
}
