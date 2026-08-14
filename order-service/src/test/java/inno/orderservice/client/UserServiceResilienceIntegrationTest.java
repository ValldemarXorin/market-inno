package inno.orderservice.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.spring6.retry.configure.RetryConfigurationProperties;
import inno.orderservice.config.RestClientConfig;
import inno.orderservice.dto.response.UserResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserServiceResilienceIntegrationTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            KafkaAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, scanBasePackages = "inno.orderservice.client")
    @Import(RestClientConfig.class)
    static class ResilienceTestApplication {
    }

    private enum HandlerMode {
        SUCCESS_AFTER_FAILURES,
        ALWAYS_404,
        ALWAYS_500,
        ALWAYS_200
    }

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private RetryConfigurationProperties retryConfigurationProperties;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static HttpServer server;
    private static final AtomicReference<HandlerMode> mode = new AtomicReference<>(HandlerMode.ALWAYS_500);
    private static final AtomicInteger requests = new AtomicInteger();

    private static final String EMAIL = "vova@gmail.com";
    private static final String USER_JSON = """
            {"id":"c0a8d1e2-0000-0000-0000-000000000001","username":"vova","surname":"khorin",
             "birthDate":"2006-01-20","email":"vova@gmail.com","active":true,
             "createdAt":"2026-01-01T12:00:00","updatedAt":"2026-01-01T12:00:00"}
            """;

    @DynamicPropertySource
    static void startServer(DynamicPropertyRegistry registry) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", UserServiceResilienceIntegrationTest::handle);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int port = server.getAddress().getPort();
        registry.add("app.user-service.base-url", () -> "http://localhost:" + port);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void reset() {
        requests.set(0);
        mode.set(HandlerMode.ALWAYS_500);
        circuitBreakerRegistry.circuitBreaker("userService").reset();
    }

    @Test
    void shouldBindRetryConfigurationFromApplication() {
        UserResponse user = callWithMode(HandlerMode.ALWAYS_200);

        assertNotNull(user);

        var properties = retryConfigurationProperties.getInstances().get("userService");
        assertNotNull(properties);
        assertEquals(Integer.valueOf(3), properties.getMaxAttempts());
        assertEquals(Duration.ofMillis(200), properties.getWaitDuration());
        assertEquals(Boolean.TRUE, properties.getEnableExponentialBackoff());
        assertEquals(Double.valueOf(2.0), properties.getExponentialBackoffMultiplier());
    }

    @Test
    void shouldBindCircuitBreakerConfigurationFromApplication() {
        UserResponse user = callWithMode(HandlerMode.ALWAYS_200);

        assertNotNull(user);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
        assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.getSlidingWindowType());
        assertEquals(10, config.getSlidingWindowSize());
        assertEquals(5, config.getMinimumNumberOfCalls());
        assertEquals(50, config.getFailureRateThreshold());
        assertEquals(Duration.ofSeconds(10), Duration.ofMillis(
                config.getWaitIntervalFunctionInOpenState().apply(1)));
        assertEquals(2, config.getPermittedNumberOfCallsInHalfOpenState());
        assertTrue(config.isAutomaticTransitionFromOpenToHalfOpenEnabled());
    }

    @Test
    void shouldRetryTransientFailuresAndReturnUser() {
        mode.set(HandlerMode.SUCCESS_AFTER_FAILURES);

        UserResponse user = userServiceClient.getUserByEmail(EMAIL);

        assertNotNull(user);
        assertEquals(EMAIL, user.email());
        assertEquals(3, requests.get());
    }

    @Test
    void shouldNotRetryClientError() {
        mode.set(HandlerMode.ALWAYS_404);

        UserResponse user = userServiceClient.getUserByEmail(EMAIL);

        assertNull(user);
        assertEquals(1, requests.get());
    }

    @Test
    void shouldOpenCircuitBreakerAndFallBackToNull() {
        mode.set(HandlerMode.ALWAYS_500);

        for (int i = 0; i < 2; i++) {
            assertNull(userServiceClient.getUserByEmail(EMAIL));
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        int performedRequests = requests.get();
        assertNull(userServiceClient.getUserByEmail(EMAIL));
        assertEquals(performedRequests, requests.get());
    }

    private UserResponse callWithMode(HandlerMode handlerMode) {
        mode.set(handlerMode);
        return userServiceClient.getUserByEmail(EMAIL);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        HandlerMode current = mode.get();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        int status;
        byte[] body;
        switch (current) {
            case ALWAYS_404 -> {
                status = 404;
                body = "{\"message\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
            }
            case SUCCESS_AFTER_FAILURES -> {
                if (requests.get() < 3) {
                    status = 503;
                    body = "{}".getBytes(StandardCharsets.UTF_8);
                } else {
                    status = 200;
                    body = USER_JSON.getBytes(StandardCharsets.UTF_8);
                }
            }
            case ALWAYS_200 -> {
                status = 200;
                body = USER_JSON.getBytes(StandardCharsets.UTF_8);
            }
            default -> {
                status = 503;
                body = "{}".getBytes(StandardCharsets.UTF_8);
            }
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}