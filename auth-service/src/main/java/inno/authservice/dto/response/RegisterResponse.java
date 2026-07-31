package inno.authservice.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String login
) {}
