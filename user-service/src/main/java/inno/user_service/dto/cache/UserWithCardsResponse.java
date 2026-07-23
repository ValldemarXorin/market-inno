package inno.user_service.dto.cache;

import inno.user_service.dto.response.PaymentCardResponse;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserWithCardsResponse(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PaymentCardResponse> cards
) implements Serializable {
}