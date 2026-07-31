package inno.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "Refresh token must not be blank")
        @Size(max = 512, message = "Refresh token is too long")
        String refreshToken
) {}
