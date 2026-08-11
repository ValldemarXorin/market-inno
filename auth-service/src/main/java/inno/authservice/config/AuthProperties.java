package inno.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        RefreshToken refreshToken,
        Bcrypt bcrypt
) {
    public record Jwt(String secret, long accessTokenTtlMinutes) {}
    public record RefreshToken(long ttlDays, int byteLength) {}
    public record Bcrypt(int strength) {}
}
