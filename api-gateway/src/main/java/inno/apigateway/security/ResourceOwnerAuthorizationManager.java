package inno.apigateway.security;

import org.springframework.http.server.PathContainer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ResourceOwnerAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final PathPattern pathPattern;
    private final String variableName;

    public ResourceOwnerAuthorizationManager(String pattern, String variableName) {
        this.pathPattern = new PathPatternParser().parse(pattern);
        this.variableName = variableName;
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .map(auth -> new AuthorizationDecision(isAdmin(auth) || isOwner(auth, context.getExchange())))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLE::equals);
    }

    private boolean isOwner(Authentication authentication, ServerWebExchange exchange) {
        String pathValue = extractPathVariable(exchange);
        if (pathValue == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return pathValue.equals(jwt.getSubject());
        }
        return pathValue.equals(principal != null ? principal.toString() : null);
    }

    private String extractPathVariable(ServerWebExchange exchange) {
        PathPattern.PathMatchInfo matchInfo = pathPattern.matchAndExtract(exchange.getRequest().getPath());
        if (matchInfo == null) {
            return null;
        }
        return matchInfo.getUriVariables().get(variableName);
    }
}
