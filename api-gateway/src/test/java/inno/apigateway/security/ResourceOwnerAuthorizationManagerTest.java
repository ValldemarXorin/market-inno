package inno.apigateway.security;

import inno.apigateway.TestTokens;
import inno.apigateway.security.config.JwtValidationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceOwnerAuthorizationManagerTest {

    private static final String PATTERN = "/api/v1/users/{id}";
    private static final String VARIABLE = "id";

    private final ResourceOwnerAuthorizationManager manager =
            new ResourceOwnerAuthorizationManager(PATTERN, VARIABLE);
    private final JwtReactiveDecoder decoder =
            new JwtReactiveDecoder(new JwtValidationProperties(TestTokens.SECRET));

    @Test
    void grantsAdminForAnyResource() {
        JwtAuthenticationToken admin = authentication(TestTokens.token(UUID.randomUUID(), "ADMIN"));

        assertThat(check(admin, "/api/v1/users/1").isGranted()).isTrue();
    }

    @Test
    void grantsUserForOwnResource() {
        UUID userId = UUID.randomUUID();
        JwtAuthenticationToken user = authentication(TestTokens.token(userId, "USER"));

        assertThat(check(user, "/api/v1/users/" + userId).isGranted()).isTrue();
    }

    @Test
    void deniesUserForAnotherUsersResource() {
        JwtAuthenticationToken user = authentication(TestTokens.token(UUID.randomUUID(), "USER"));

        assertThat(check(user, "/api/v1/users/" + UUID.randomUUID()).isGranted()).isFalse();
    }

    @Test
    void deniesUnauthenticatedRequest() {
        AuthorizationContext context = context("/api/v1/users/1");

        AuthorizationDecision decision = manager.check(Mono.empty(), context).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void deniesPathThatDoesNotMatchPattern() {
        JwtAuthenticationToken user = authentication(TestTokens.token(UUID.randomUUID(), "USER"));

        assertThat(check(user, "/api/v1/users/1/orders").isGranted()).isFalse();
    }

    private AuthorizationDecision check(JwtAuthenticationToken authentication, String path) {
        return manager.check(Mono.just(authentication), context(path)).block();
    }

    private AuthorizationContext context(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return new AuthorizationContext(exchange);
    }

    private JwtAuthenticationToken authentication(String token) {
        Jwt jwt = decoder.decode(token).block();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
    }
}
