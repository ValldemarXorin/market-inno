package inno.authservice.service;

import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.entity.Role;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.LoginAlreadyExistsException;
import inno.authservice.exception.custom_exception.UserNotFoundException;
import inno.authservice.mapper.UserCredentialsMapper;
import inno.authservice.messaging.UserCreatedEvent;
import inno.authservice.repository.RefreshTokenRepository;
import inno.authservice.repository.UserCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserCredentialsServiceTest {

    @Mock private UserCredentialsRepository userCredentialsRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserCredentialsMapper userCredentialsMapper;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserCredentialsService userCredentialsService;

    @BeforeEach
    void setUp() {
        userCredentialsService = new UserCredentialsService(userCredentialsRepository, passwordEncoder, userCredentialsMapper, refreshTokenRepository, eventPublisher);
    }

    @Test
    void register_shouldCreateUserWithUserRoleAndHashedPassword() {
        when(userCredentialsRepository.existsByLogin("john")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userCredentialsRepository.save(any(UserCredentials.class)))
                .thenAnswer(invocation -> {
                    UserCredentials entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });
        when(userCredentialsMapper.toRegisterResponse(any(UserCredentials.class)))
                .thenAnswer(invocation -> {
                    UserCredentials entity = invocation.getArgument(0);
                    return new RegisterResponse(entity.getId(), entity.getLogin());
                });

        userCredentialsService.register("john", "password123", "John", "Doe",
                LocalDate.of(1990, Month.JANUARY, 1), "john@example.com");

        ArgumentCaptor<UserCredentials> captor = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userCredentialsRepository).save(captor.capture());

        UserCredentials saved = captor.getValue();
        assertThat(saved.getLogin()).isEqualTo("john");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getActive()).isTrue();

        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(saved.getId());
        assertThat(event.username()).isEqualTo("John");
        assertThat(event.surname()).isEqualTo("Doe");
        assertThat(event.birthDate()).isEqualTo(LocalDate.of(1990, Month.JANUARY, 1));
        assertThat(event.email()).isEqualTo("john@example.com");
    }

    @Test
    void register_shouldThrowLoginAlreadyExists_whenLoginTaken() {
        when(userCredentialsRepository.existsByLogin("john")).thenReturn(true);

        assertThatThrownBy(() -> userCredentialsService.register("john", "password123",
                "John", "Doe", LocalDate.of(1990, Month.JANUARY, 1), "john@example.com"))
                .isInstanceOf(LoginAlreadyExistsException.class);

        verify(userCredentialsRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void setActive_shouldUpdateActiveFlagAndRevokeTokens_whenUserExists() {
        UUID id = UUID.randomUUID();
        UserCredentials user = new UserCredentials();
        user.setId(id);
        user.setActive(true);

        when(userCredentialsRepository.findById(id)).thenReturn(Optional.of(user));

        userCredentialsService.deactivate(id);

        assertThat(user.getActive()).isFalse();
        verify(userCredentialsRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserCredentialsId(id);
    }

    @Test
    void setActive_shouldThrowUserNotFound_whenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userCredentialsRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userCredentialsService.deactivate(id))
                .isInstanceOf(UserNotFoundException.class);

        verify(userCredentialsRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserCredentialsId(any());
    }

    @Test
    void activate_shouldSetActiveTrue_whenUserExists() {
        UUID id = UUID.randomUUID();
        UserCredentials user = new UserCredentials();
        user.setId(id);
        user.setActive(false);

        when(userCredentialsRepository.findById(id)).thenReturn(Optional.of(user));

        userCredentialsService.activate(id);

        assertThat(user.getActive()).isTrue();
        verify(userCredentialsRepository).save(user);
        verify(refreshTokenRepository, never()).revokeAllByUserCredentialsId(any());
    }

    @Test
    void getAll_shouldReturnMappedResponses() {
        UserCredentials user = new UserCredentials();
        user.setId(UUID.randomUUID());
        user.setLogin("john");
        user.setRole(Role.USER);
        user.setActive(true);

        UserCredentialsResponse mapped = new UserCredentialsResponse(
                user.getId(), user.getLogin(), user.getRole(), user.getActive(), LocalDateTime.now()
        );

        when(userCredentialsRepository.findAll()).thenReturn(List.of(user));
        when(userCredentialsMapper.toResponse(user)).thenReturn(mapped);

        List<UserCredentialsResponse> result = userCredentialsService.getAll();

        assertThat(result).containsExactly(mapped);
    }
}