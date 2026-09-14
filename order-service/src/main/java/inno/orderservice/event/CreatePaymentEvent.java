package inno.orderservice.event;

import java.util.UUID;

public record CreatePaymentEvent(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status
) {
}