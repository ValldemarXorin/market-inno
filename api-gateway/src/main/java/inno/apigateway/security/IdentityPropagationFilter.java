package inno.apigateway.security;

import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class IdentityPropagationFilter implements WebFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final int ORDER = -99;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = sanitize(exchange);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(this::hasJwt)
                .map(authentication -> (Jwt) authentication.getPrincipal())
                .flatMap(jwt -> chain.filter(withIdentityHeaders(sanitized, jwt)))
                .switchIfEmpty(chain.filter(sanitized));
    }

    private boolean hasJwt(Authentication authentication) {
        return authentication.getPrincipal() instanceof Jwt;
    }

    private ServerWebExchange sanitize(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                }))
                .build();
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role == null) {
            return exchange;
        }
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(USER_ID_HEADER, jwt.getSubject());
                    headers.set(USER_ROLE_HEADER, role);
                }))
                .build();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
