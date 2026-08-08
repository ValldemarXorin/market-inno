package inno.user_service.service;

import inno.user_service.config.CacheNames;
import inno.user_service.config.RedisConfig;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.dao.specification.UserSpecification;
import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.UpdateUserRequest;
import inno.user_service.dto.response.UserResponse;
import inno.user_service.entity.User;
import inno.user_service.event.UserActivatedEvent;
import inno.user_service.event.UserCreatedEvent;
import inno.user_service.event.UserDeactivatedEvent;
import inno.user_service.exception.custom_exception.EmailAlreadyExistsException;
import inno.user_service.exception.custom_exception.UserNotFoundException;
import inno.user_service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @CachePut(cacheNames = CacheNames.USERS_CACHE, key = "#result.id")
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        if (userRepository.existsByEmail(createUserRequest.email())) {
            throw new EmailAlreadyExistsException(createUserRequest.email());
        }

        User user = userMapper.toEntity(createUserRequest);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Cacheable(cacheNames = CacheNames.USERS_CACHE, key = "#id")
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toResponse(user);
    }

    public Page<UserResponse> getAllUsers(String name, String surname, Pageable pageable) {
        return userRepository
                .findAll(UserSpecification.filterBy(name, surname), pageable)
                .map(userMapper::toResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.USERS_CACHE, key = "#id")
    public UserResponse updateUser(UUID id, UpdateUserRequest updateUserRequest) {
        int updated = userRepository.updateUserDetails(
                id,
                updateUserRequest.username(),
                updateUserRequest.surname(),
                updateUserRequest.birthDate(),
                updateUserRequest.email()
        );

        if (updated == 0) {
            throw new UserNotFoundException(id);
        }

        return getUserById(id);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.USERS_CACHE, key = "#id")
    public void setUserActive(UUID id, boolean isActive) {
        int updated = userRepository.setActiveNative(id, isActive);

        if (updated == 0) {
            throw new UserNotFoundException(id);
        }

        eventPublisher.publishEvent(
                isActive ? new UserActivatedEvent(id) : new UserDeactivatedEvent(id)
        );
    }

    @Transactional
    public void provisionUser(UserCreatedEvent event) {
        if (userRepository.existsById(event.userId())) {
            log.warn("User already exists for provisioning event, skipping: userId={}", event.userId());
            return;
        }

        User user = new User();
        user.setId(event.userId());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate provisioning attempt ignored: userId={}", event.userId(), e);
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USERS_CACHE, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CARDS_CACHE, key = "#id")
    })
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userRepository.deleteById(id);
    }
}
