package inno.authservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.entity.OutboxEvent;
import inno.authservice.entity.Role;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.LoginAlreadyExistsException;
import inno.authservice.exception.custom_exception.UserNotFoundException;
import inno.authservice.mapper.UserCredentialsMapper;
import inno.authservice.messaging.UserCreatedEvent;
import inno.authservice.repository.OutboxEventRepository;
import inno.authservice.repository.RefreshTokenRepository;
import inno.authservice.repository.UserCredentialsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCredentialsService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserCredentialsMapper userCredentialsMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RegisterResponse register(String login, String rawPassword) {
        if (userCredentialsRepository.existsByLogin(login)) {
            log.warn("Registration rejected, login already taken");
            throw new LoginAlreadyExistsException("Login already taken: " + login);
        }

        UserCredentials user = new UserCredentials();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setActive(true);

        UserCredentials saved = userCredentialsRepository.save(user);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(OutboxEvent.TYPE_USER_CREATED);
        outboxEvent.setAggregateId(saved.getId());
        outboxEvent.setPayload(toPayload(new UserCreatedEvent(saved.getId())));
        outboxEventRepository.save(outboxEvent);

        log.info("Registered new user credentials: id={}", saved.getId());
        log.info("Outbox event created: id={}, type={}, aggregateId={}",
                outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId());

        return userCredentialsMapper.toRegisterResponse(saved);
    }

    private String toPayload(UserCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UserCreatedEvent", e);
        }
    }

    public List<UserCredentialsResponse> getAll() {
        return userCredentialsRepository.findAll().stream()
                .map(userCredentialsMapper::toResponse)
                .toList();
    }

    @Transactional
    public void activate(UUID id) {
        setActive(id, true);
        log.info("User {} activated", id);
    }

    @Transactional
    public void deactivate(UUID id) {
        setActive(id, false);
        refreshTokenRepository.revokeAllByUserCredentialsId(id);
        log.info("User {} deactivated, refresh tokens revoked", id);
    }

    public UserCredentialsResponse getById(UUID id) {
        UserCredentials user = userCredentialsRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        return userCredentialsMapper.toResponse(user);
    }

    private void setActive(UUID id, boolean active) {
        UserCredentials user = userCredentialsRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        user.setActive(active);
        userCredentialsRepository.save(user);
    }
}
