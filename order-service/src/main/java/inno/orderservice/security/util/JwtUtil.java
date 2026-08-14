package inno.orderservice.security.util;

import inno.orderservice.security.config.JwtValidationProperties;
import inno.orderservice.security.entity.Role;
import inno.orderservice.security.exception.InvalidTokenException;
import inno.orderservice.security.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;

    public JwtUtil(JwtValidationProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes());
    }

    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid access token");
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }
}