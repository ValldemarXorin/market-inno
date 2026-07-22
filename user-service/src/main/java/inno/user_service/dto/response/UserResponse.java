package inno.user_service.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
