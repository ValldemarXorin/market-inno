package inno.authservice.integration;

import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.TokenPairResponse;
import inno.authservice.entity.RefreshToken;
import inno.authservice.exception.custom_exception.UserDeactivatedException;
import inno.authservice.messaging.UserStatusEvent;
import inno.authservice.repository.RefreshTokenRepository;
import inno.authservice.service.AuthService;
import inno.authservice.service.UserCredentialsService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "user-status-events")
class UserStatusKafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("authservice")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("auth.jwt.secret",
                () -> "integration-test-secret-at-least-32-bytes-long!!");
    }

    @Autowired
    private UserCredentialsService userCredentialsService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.user-status:user-status-events}")
    private String userStatusTopic;

    @Test
    void deactivationShouldBlockLoginAndRefresh_thenActivationRestoresLogin() throws Exception {
        RegisterResponse registered =
                userCredentialsService.register("kafka_integration_user", "password123");

        TokenPairResponse tokens =
                authService.login("kafka_integration_user", "password123");

        kafkaTemplate.send(userStatusTopic, registered.id().toString(),
                        new UserStatusEvent(registered.id(), false))
                .get();

        await(() -> !userCredentialsService.getById(registered.id()).active());

        assertThatThrownBy(() -> authService.login("kafka_integration_user", "password123"))
                .isInstanceOf(UserDeactivatedException.class);

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hash(tokens.refreshToken()))
                .orElseThrow();
        assertThat(stored.getRevoked()).isTrue();

        kafkaTemplate.send(userStatusTopic, registered.id().toString(),
                        new UserStatusEvent(registered.id(), true))
                .get();

        await(() -> userCredentialsService.getById(registered.id()).active());

        assertThat(authService.login("kafka_integration_user", "password123")).isNotNull();
    }

    private void await(Supplier<Boolean> condition) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(condition::get);
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
