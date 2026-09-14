package inno.paymentservice.event;

import inno.paymentservice.entity.PaymentStatus;

import java.util.UUID;

public record CreatePaymentEvent(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status
) {
}