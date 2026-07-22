package inno.user_service.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String surname,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 255) String email
) {
}
