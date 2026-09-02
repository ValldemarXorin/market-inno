package inno.apigateway.security.config;

import inno.apigateway.security.JwtAuthenticationConverter;
import inno.apigateway.security.JwtReactiveDecoder;
import inno.apigateway.security.ResourceOwnerAuthorizationManager;
import inno.apigateway.security.RestAccessDeniedHandler;
import inno.apigateway.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableConfigurationProperties(JwtValidationProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
    };

    private final JwtReactiveDecoder jwtReactiveDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()

                        // admin-only
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/by-email/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/users/{id}/active").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/users/{id}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/cards").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/orders/{id}").hasRole("ADMIN")

                        // admin or resource owner (path variable vs JWT sub)
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/{id}")
                        .access(adminOrResourceOwner("/api/v1/users/{id}", "id"))
                        .pathMatchers(HttpMethod.PUT, "/api/v1/users/{id}")
                        .access(adminOrResourceOwner("/api/v1/users/{id}", "id"))
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/{userId}/cards")
                        .access(adminOrResourceOwner("/api/v1/users/{userId}/cards", "userId"))
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/{userId}/cards")
                        .access(adminOrResourceOwner("/api/v1/users/{userId}/cards", "userId"))
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/{userId}/orders")
                        .access(adminOrResourceOwner("/api/v1/users/{userId}/orders", "userId"))

                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtReactiveDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .build();
    }

    private ResourceOwnerAuthorizationManager adminOrResourceOwner(String pattern, String variableName) {
        return new ResourceOwnerAuthorizationManager(pattern, variableName);
    }
}
