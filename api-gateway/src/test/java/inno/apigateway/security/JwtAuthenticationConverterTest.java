package inno.apigateway.security;

import inno.apigateway.TestTokens;
import inno.apigateway.security.config.JwtValidationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    private final JwtReactiveDecoder decoder =
            new JwtReactiveDecoder(new JwtValidationProperties(TestTokens.SECRET));

    @Test
    void mapsRoleClaimToAuthorityAndPreservesUserId() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = decoder.decode(TestTokens.token(userId, "USER")).block();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt).block();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(((Jwt) authentication.getPrincipal()).getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void authenticatesTokenWithoutRoleClaimWithNoAuthorities() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", UUID.randomUUID().toString()));

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt).block();

        assertThat(authentication.getAuthorities()).isEmpty();
    }
}
