package inno.paymentservice.dto.response;

import inno.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        LocalDateTime timestamp,
        BigDecimal paymentAmount
) {
}