package inno.orderservice.security.chain;

import inno.orderservice.security.exception.InvalidTokenException;
import inno.orderservice.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null) {
            try {
                if (!header.startsWith(BEARER_PREFIX)) {
                    throw new InvalidTokenException("Authorization header must start with 'Bearer '");
                }
                String rawToken = header.substring(BEARER_PREFIX.length());
                Claims claims = jwtUtil.parseAndValidate(rawToken);

                UUID userId = jwtUtil.extractUserId(claims);
                List<GrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority(ROLE_PREFIX + jwtUtil.extractRole(claims).name()));

                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException e) {
                log.debug("Rejected invalid/malformed JWT: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}