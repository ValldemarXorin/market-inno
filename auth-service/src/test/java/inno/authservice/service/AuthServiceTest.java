package inno.authservice.service;

import inno.authservice.config.AuthProperties;
import inno.authservice.dto.response.TokenPairResponse;
import inno.authservice.dto.response.TokenValidationResponse;
import inno.authservice.entity.RefreshToken;
import inno.authservice.entity.Role;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.*;
import inno.authservice.repository.RefreshTokenRepository;
import inno.authservice.repository.UserCredentialsRepository;
import inno.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserCredentialsRepository userCredentialsRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    private AuthService authService;
    private UserCredentials activeUser;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties(
                new AuthProperties.Jwt("test-secret-at-least-32-bytes-long!!", 15),
                new AuthProperties.RefreshToken(30, 64),
                new AuthProperties.Bcrypt(4)
        );
        authService = new AuthService(userCredentialsRepository, refreshTokenRepository, passwordEncoder, jwtUtil, authProperties);

        activeUser = new UserCredentials();
        activeUser.setId(UUID.randomUUID());
        activeUser.setLogin("john");
        activeUser.setPasswordHash("hashed");
        activeUser.setRole(Role.USER);
        activeUser.setActive(true);
    }

    @Test
    void login_shouldReturnTokenPair_whenCredentialsAreValid() {
        when(userCredentialsRepository.findByLogin("john")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateAccessToken(activeUser.getId(), Role.USER)).thenReturn("access-token");

        TokenPairResponse result = authService.login("john", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserCredentials()).isEqualTo(activeUser);
        assertThat(captor.getValue().getRevoked()).isFalse();
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenUserNotFound() {
        when(userCredentialsRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder, jwtUtil, refreshTokenRepository);
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenPasswordDoesNotMatch() {
        when(userCredentialsRepository.findByLogin("john")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("john", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtUtil, refreshTokenRepository);
    }

    @Test
    void login_shouldThrowUserDeactivated_whenUserIsInactive() {
        activeUser.setActive(false);
        when(userCredentialsRepository.findByLogin("john")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("john", "password123"))
                .isInstanceOf(UserDeactivatedException.class);

        verifyNoInteractions(jwtUtil, refreshTokenRepository);
    }

    @Test
    void refresh_shouldRotateToken_whenTokenIsValid() {
        RefreshToken stored = new RefreshToken();
        stored.setUserCredentials(activeUser);
        stored.setFamilyId(UUID.randomUUID());
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(jwtUtil.generateAccessToken(activeUser.getId(), Role.USER)).thenReturn("new-access-token");

        TokenPairResponse result = authService.refresh("some-raw-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(stored.getRevoked()).isTrue(); // старый токен помечен использованным

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getFamilyId()).isEqualTo(stored.getFamilyId()); // family не меняется при rotation
        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any());
    }

    @Test
    void refresh_shouldRevokeWholeFamilyAndThrow_whenTokenAlreadyUsed() {
        UUID familyId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setUserCredentials(activeUser);
        stored.setFamilyId(familyId);
        stored.setRevoked(true);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(RefreshTokenReusedException.class);

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
        verify(jwtUtil, never()).generateAccessToken(any(), any());
    }

    @Test
    void refresh_shouldThrowTokenExpired_whenTokenIsExpired() {
        RefreshToken stored = new RefreshToken();
        stored.setUserCredentials(activeUser);
        stored.setFamilyId(UUID.randomUUID());
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(TokenExpiredException.class);

        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any());
    }

    @Test
    void refresh_shouldThrowUserDeactivated_whenOwnerIsInactive() {
        activeUser.setActive(false);
        RefreshToken stored = new RefreshToken();
        stored.setUserCredentials(activeUser);
        stored.setFamilyId(UUID.randomUUID());
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("some-token"))
                .isInstanceOf(UserDeactivatedException.class);
    }

    @Test
    void refresh_shouldThrowInvalidToken_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validate_shouldDelegateToJwtService() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseAndValidate("access-token")).thenReturn(claims);
        when(jwtUtil.extractUserId(claims)).thenReturn(activeUser.getId());
        when(jwtUtil.extractRole(claims)).thenReturn(Role.USER);

        TokenValidationResponse result = authService.validate("access-token");

        assertThat(result.userId()).isEqualTo(activeUser.getId());
        assertThat(result.role()).isEqualTo(Role.USER);
    }
}
