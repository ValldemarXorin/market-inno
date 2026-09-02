package inno.apigateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewaySecurityIntegrationTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void loginEndpointIsPublic() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void registerEndpointIsPublic() {
        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        webTestClient.get().uri("/api/v1/users/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.message").isEqualTo("Authentication required");
    }

    @Test
    void protectedEndpointWithMalformedTokenReturns401() {
        webTestClient.get().uri("/api/v1/users/1")
                .headers(headers -> headers.setBearerAuth("not-a-valid-jwt"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithExpiredTokenReturns401() {
        String expiredToken = TestTokens.expiredToken(UUID.randomUUID(), "USER");

        webTestClient.get().uri("/api/v1/users/1")
                .headers(headers -> headers.setBearerAuth(expiredToken))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithTokenSignedByUnknownKeyReturns401() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-at-least-32-bytes-long!!".getBytes()))
                .compact();

        webTestClient.get().uri("/api/v1/users/1")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithValidTokenIsAuthenticated() {
        String token = TestTokens.token(UUID.randomUUID(), "USER");

        webTestClient.get().uri("/api/v1/users/1")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }
}
