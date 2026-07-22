package inno.user_service.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UpdateUserRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Surname must not be blank")
        @Size(max = 100, message = "Surname must not exceed 100 characters")
        String surname,

        @NotNull(message = "Birth date must not be null")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email
) {
}
