package inno.user_service.event;

import java.time.LocalDate;
import java.util.UUID;

public record UserCreatedEvent(
        UUID userId,
        String username,
        String surname,
        LocalDate birthDate,
        String email
) {
}
