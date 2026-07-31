package inno.authservice.service;

import inno.authservice.config.AuthProperties;
import inno.authservice.dto.response.TokenPairResponse;
import inno.authservice.dto.response.TokenValidationResponse;
import inno.authservice.entity.RefreshToken;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.*;
import inno.authservice.repository.RefreshTokenRepository;
import inno.authservice.repository.UserCredentialsRepository;
import inno.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 64;

    private final UserCredentialsRepository userCredentialsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthProperties authProperties;

    @Transactional
    public TokenPairResponse login(String login, String rawPassword) {
        UserCredentials credentialsUser = userCredentialsRepository.findByLogin(login)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid login or password"));

        if (!passwordEncoder.matches(rawPassword, credentialsUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid login or password");
        }

        if (!credentialsUser.getActive()) {
            throw new UserDeactivatedException("User account is deactivated");
        }

        return issueTokenPair(credentialsUser, UUID.randomUUID());
    }

    @Transactional
    public TokenPairResponse refresh(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (existing.getRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(existing.getFamilyId());
            throw new RefreshTokenReusedException("Refresh token reuse detected, session revoked");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            throw new TokenExpiredException("Refresh token expired");
        }

        UserCredentials user = existing.getUserCredentials();
        if (!user.getActive()) {
            throw new UserDeactivatedException("User account is deactivated");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokenPair(user, existing.getFamilyId());
    }

    public TokenValidationResponse validate(String rawAccessToken) {
        Claims claims = jwtUtil.parseAndValidate(rawAccessToken);
        return new TokenValidationResponse(
                jwtUtil.extractUserId(claims),
                jwtUtil.extractRole(claims)
        );
    }

    private TokenPairResponse issueTokenPair(UserCredentials user, UUID familyId) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole());

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserCredentials(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(authProperties.refreshToken().ttlDays()));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new TokenPairResponse(accessToken, rawRefreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

