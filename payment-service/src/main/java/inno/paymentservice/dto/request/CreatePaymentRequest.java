package inno.paymentservice.dto.request;

import inno.paymentservice.entity.PaymentCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "Order id must not be null")
        UUID orderId,

        @NotNull(message = "User id must not be null")
        UUID userId,

        @NotNull(message = "Timestamp must not be null")
        LocalDateTime timestamp,

        @NotNull(message = "Payment amount must not be null")
        @DecimalMin(value = "0.01", message = "Payment amount must be positive")
        BigDecimal paymentAmount,

        @NotNull(message = "Payment currency must not be null")
        PaymentCurrency currency
) {
}