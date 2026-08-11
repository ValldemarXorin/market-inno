package inno.authservice.dto.response;

import inno.authservice.entity.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserCredentialsResponse(
        UUID id,
        String login,
        Role role,
        Boolean active,
        LocalDateTime createdAt
) {}
