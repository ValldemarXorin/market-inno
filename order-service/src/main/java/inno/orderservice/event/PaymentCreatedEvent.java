package inno.orderservice.event;

import java.util.UUID;

public record PaymentCreatedEvent(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status
) {
}