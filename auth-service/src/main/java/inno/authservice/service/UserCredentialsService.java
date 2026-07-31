package inno.authservice.service;

import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.entity.Role;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.LoginAlreadyExistsException;
import inno.authservice.exception.custom_exception.UserNotFoundException;
import inno.authservice.mapper.UserCredentialsMapper;
import inno.authservice.repository.UserCredentialsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserCredentialsService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserCredentialsMapper userCredentialsMapper;

    @Transactional
    public RegisterResponse register(String login, String rawPassword) {
        if (userCredentialsRepository.existsByLogin(login)) {
            throw new LoginAlreadyExistsException("Login already taken: " + login);
        }

        UserCredentials user = new UserCredentials();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setActive(true);

        UserCredentials saved = userCredentialsRepository.save(user);
        return userCredentialsMapper.toRegisterResponse(saved);
    }

    public List<UserCredentialsResponse> getAll() {
        return userCredentialsRepository.findAll().stream()
                .map(userCredentialsMapper::toResponse)
                .toList();
    }

    @Transactional
    public void activate(UUID id) {
        setActive(id, true);
    }

    @Transactional
    public void deactivate(UUID id) {
        setActive(id, false);
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
