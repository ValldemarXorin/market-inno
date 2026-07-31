package inno.authservice.dto.response;

import inno.authservice.entity.Role;

import java.util.UUID;

public record TokenValidationResponse(
        UUID userId,
        Role role
) {}
