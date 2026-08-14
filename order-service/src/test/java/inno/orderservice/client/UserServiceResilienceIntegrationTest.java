package inno.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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

    private static WireMockServer wireMockServer;

    private static final String EMAIL = "vova@gmail.com";
    private static final String USER_JSON = """
            {"id":"c0a8d1e2-0000-0000-0000-000000000001","username":"vova","surname":"khorin",
             "birthDate":"2006-01-20","email":"vova@gmail.com","active":true,
             "createdAt":"2026-01-01T12:00:00","updatedAt":"2026-01-01T12:00:00"}
            """;

    @DynamicPropertySource
    static void startServer(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("app.user-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void reset() {
        wireMockServer.resetAll();
        stubUserService(HandlerMode.ALWAYS_500);
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
        stubUserService(HandlerMode.SUCCESS_AFTER_FAILURES);

        UserResponse user = userServiceClient.getUserByEmail(EMAIL);

        assertNotNull(user);
        assertEquals(EMAIL, user.email());
        assertEquals(3, wireMockServer.getAllServeEvents().size());
    }

    @Test
    void shouldNotRetryClientError() {
        stubUserService(HandlerMode.ALWAYS_404);

        UserResponse user = userServiceClient.getUserByEmail(EMAIL);

        assertNull(user);
        assertEquals(1, wireMockServer.getAllServeEvents().size());
    }

    @Test
    void shouldOpenCircuitBreakerAndFallBackToNull() {
        stubUserService(HandlerMode.ALWAYS_500);

        for (int i = 0; i < 2; i++) {
            assertNull(userServiceClient.getUserByEmail(EMAIL));
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        int performedRequests = wireMockServer.getAllServeEvents().size();
        assertNull(userServiceClient.getUserByEmail(EMAIL));
        assertEquals(performedRequests, wireMockServer.getAllServeEvents().size());
    }

    private UserResponse callWithMode(HandlerMode handlerMode) {
        stubUserService(handlerMode);
        return userServiceClient.getUserByEmail(EMAIL);
    }

    private static void stubUserService(HandlerMode mode) {
        wireMockServer.resetAll();
        switch (mode) {
            case ALWAYS_404 -> stubNotFound();
            case SUCCESS_AFTER_FAILURES -> stubSuccessAfterFailures();
            case ALWAYS_200 -> stubOk();
            default -> stubServerError();
        }
    }

    private static void stubOk() {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(USER_JSON)));
    }

    private static void stubNotFound() {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"not found\"}")));
    }

    private static void stubServerError() {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    private static void stubSuccessAfterFailures() {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .inScenario("user-service-success-after-failures")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}"))
                .willSetStateTo("second-attempt"));

        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .inScenario("user-service-success-after-failures")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}"))
                .willSetStateTo("third-attempt"));

        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .inScenario("user-service-success-after-failures")
                .whenScenarioStateIs("third-attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(USER_JSON)));
    }

    private static String userUrlPattern() {
        return "/users/by-email/.*";
    }
}
