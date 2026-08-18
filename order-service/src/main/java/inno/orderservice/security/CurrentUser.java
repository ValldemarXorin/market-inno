package inno.orderservice.security;

import inno.orderservice.security.entity.Role;
import inno.orderservice.security.exception.InvalidIdentityException;
import inno.orderservice.security.exception.MissingIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

@Component
@RequestScope
@RequiredArgsConstructor
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String AUTHENTICATION_REQUIRED = "Authentication required";

    private final HttpServletRequest request;

    public UUID id() {
        String raw = request.getHeader(USER_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new MissingIdentityException(AUTHENTICATION_REQUIRED);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidIdentityException("X-User-Id is not a valid UUID");
        }
    }

    public Role role() {
        String raw = request.getHeader(USER_ROLE_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new MissingIdentityException(AUTHENTICATION_REQUIRED);
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidIdentityException("X-User-Role is invalid");
        }
    }

    public boolean isAdmin() {
        return role() == Role.ADMIN;
    }
}