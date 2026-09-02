package inno.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayAuthorizationIntegrationTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void refreshEndpointIsPublic() {
        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .exchange()
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void userCreationRequiresAuthentication() {
        webTestClient.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void userTokenCannotAccessAdminOnlyEndpoints() {
        String token = TestTokens.token(UUID.randomUUID(), "USER");

        getWithToken("/api/v1/users", token).expectStatus().isForbidden();
        getWithToken("/api/v1/users/by-email/user@example.com", token).expectStatus().isForbidden();
        getWithToken("/api/v1/cards", token).expectStatus().isForbidden();
        getWithToken("/api/v1/orders", token).expectStatus().isForbidden();

        patchWithToken("/api/v1/users/" + UUID.randomUUID() + "/active", token).expectStatus().isForbidden();
        deleteWithToken("/api/v1/users/" + UUID.randomUUID(), token).expectStatus().isForbidden();
        deleteWithToken("/api/v1/orders/" + UUID.randomUUID(), token).expectStatus().isForbidden();
    }

    @Test
    void userTokenCanAccessOwnResources() {
        UUID userId = UUID.randomUUID();
        String token = TestTokens.token(userId, "USER");

        getWithToken("/api/v1/users/" + userId, token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        putWithToken("/api/v1/users/" + userId, token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        postWithToken("/api/v1/users/" + userId + "/cards", token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        getWithToken("/api/v1/users/" + userId + "/cards", token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        getWithToken("/api/v1/users/" + userId + "/orders", token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void userTokenCannotAccessAnotherUsersResources() {
        UUID otherUserId = UUID.randomUUID();
        String token = TestTokens.token(UUID.randomUUID(), "USER");

        getWithToken("/api/v1/users/" + otherUserId, token).expectStatus().isForbidden();
        getWithToken("/api/v1/users/" + otherUserId + "/cards", token).expectStatus().isForbidden();
        getWithToken("/api/v1/users/" + otherUserId + "/orders", token).expectStatus().isForbidden();
    }

    @Test
    void adminTokenCanAccessAdminOnlyEndpoints() {
        String token = TestTokens.token(UUID.randomUUID(), "ADMIN");

        getWithToken("/api/v1/users", token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        getWithToken("/api/v1/users/" + UUID.randomUUID(), token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        getWithToken("/api/v1/orders", token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void dataDependentEndpointsRemainAccessibleToAuthenticatedUsers() {
        UUID userId = UUID.randomUUID();
        String token = TestTokens.token(userId, "USER");

        getWithToken("/api/v1/cards/" + UUID.randomUUID(), token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        putWithToken("/api/v1/cards/" + UUID.randomUUID(), token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        getWithToken("/api/v1/orders/" + UUID.randomUUID(), token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
        putWithToken("/api/v1/orders/" + UUID.randomUUID(), token)
                .expectStatus().value(not(equalTo(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void forbiddenReturnsJsonErrorResponse() {
        String token = TestTokens.token(UUID.randomUUID(), "USER");

        getWithToken("/api/v1/users", token)
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("Forbidden")
                .jsonPath("$.message").isEqualTo("You do not have permission to access this resource")
                .jsonPath("$.path").isEqualTo("/api/v1/users");
    }

    private WebTestClient.ResponseSpec getWithToken(String uri, String token) {
        return webTestClient.get().uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private WebTestClient.ResponseSpec postWithToken(String uri, String token) {
        return webTestClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private WebTestClient.ResponseSpec putWithToken(String uri, String token) {
        return webTestClient.put().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private WebTestClient.ResponseSpec patchWithToken(String uri, String token) {
        return webTestClient.patch().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{}"))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private WebTestClient.ResponseSpec deleteWithToken(String uri, String token) {
        return webTestClient.delete().uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }
}
