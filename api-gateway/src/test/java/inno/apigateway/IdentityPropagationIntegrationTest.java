package inno.apigateway;

import inno.apigateway.security.IdentityPropagationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityPropagationIntegrationTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CaptureWebFilter captureWebFilter;

    @BeforeEach
    void resetCapture() {
        captureWebFilter.reset();
    }

    @Test
    void forwardsTrustedIdentityForValidJwt() {
        UUID userId = UUID.randomUUID();
        String authorization = "Bearer " + TestTokens.token(userId, "USER");

        webTestClient.get().uri("/api/v1/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isEqualTo(userId.toString());
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isEqualTo("USER");
        assertThat(captured.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(authorization);
    }

    @Test
    void overwritesClientSuppliedIdentityHeadersWithTrustedValues() {
        UUID userId = UUID.randomUUID();
        String token = TestTokens.token(userId, "USER");

        webTestClient.get().uri("/api/v1/users/" + userId)
                .headers(headers -> headers.setBearerAuth(token))
                .header(IdentityPropagationFilter.USER_ID_HEADER, "forged-user-id")
                .header(IdentityPropagationFilter.USER_ROLE_HEADER, "ADMIN")
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isEqualTo(userId.toString());
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isEqualTo("USER");
    }

    @Test
    void doesNotInjectIdentityHeadersForUnauthenticatedRequest() {
        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isNull();
    }

    @Test
    void stripsClientSuppliedIdentityHeadersForUnauthenticatedRequest() {
        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .header(IdentityPropagationFilter.USER_ID_HEADER, "forged-user-id")
                .header(IdentityPropagationFilter.USER_ROLE_HEADER, "ADMIN")
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isNull();
    }

    @Test
    void doesNotInjectIdentityHeadersForJwtWithoutRole() {
        UUID userId = UUID.randomUUID();
        String authorization = "Bearer " + TestTokens.tokenWithoutRole(userId);

        webTestClient.get().uri("/api/v1/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isNull();
    }

    @Test
    void preservesAuthorizationHeaderForPublicEndpointWithValidJwt() {
        UUID userId = UUID.randomUUID();
        String authorization = "Bearer " + TestTokens.token(userId, "USER");

        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(IdentityPropagationFilter.USER_ID_HEADER, "forged-user-id")
                .header(IdentityPropagationFilter.USER_ROLE_HEADER, "ADMIN")
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));

        HttpHeaders captured = captureWebFilter.last();
        assertThat(captured.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(authorization);
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isEqualTo(userId.toString());
        assertThat(captured.getFirst(IdentityPropagationFilter.USER_ROLE_HEADER)).isEqualTo("USER");
    }

    @TestConfiguration
    static class CaptureConfiguration {

        @Bean
        @Order(-98)
        CaptureWebFilter captureWebFilter() {
            return new CaptureWebFilter();
        }
    }

    static class CaptureWebFilter implements WebFilter {

        private final List<HttpHeaders> captured = new ArrayList<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            captured.add(new HttpHeaders(exchange.getRequest().getHeaders()));
            return chain.filter(exchange);
        }

        HttpHeaders last() {
            return captured.get(captured.size() - 1);
        }

        void reset() {
            captured.clear();
        }
    }
}
