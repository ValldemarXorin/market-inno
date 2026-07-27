package inno.user_service.dto.response;

import inno.user_service.util.MaskingUtil;

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
    @Override
    public String toString() {
        return "PaymentCardResponse[id=" + id
                + ", userId=" + userId
                + ", number=" + MaskingUtil.maskCardNumber(number)
                + ", holder=" + holder
                + ", expirationDate=" + expirationDate
                + ", active=" + active
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + "]";
    }
}
