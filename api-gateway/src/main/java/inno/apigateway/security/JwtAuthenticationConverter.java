package inno.apigateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final String ROLE_CLAIM = "role";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        if (role == null || role.isBlank()) {
            return Mono.error(new BadJwtException("Missing role claim"));
        }
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        return Mono.just(new JwtAuthenticationToken(jwt, authorities));
    }
}
