package inno.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Login must not be blank")
        @Size(max = 100, message = "Login must be at most 100 characters")
        String login,

        @NotBlank(message = "Password must not be blank")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password
) {}
