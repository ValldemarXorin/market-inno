package inno.user_service.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCardResponse(
        UUID id,
        UUID userId,
        String number,
        String holder,
        LocalDate expirationDate,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
