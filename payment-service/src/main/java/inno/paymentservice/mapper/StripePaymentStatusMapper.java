package inno.paymentservice.service;

import inno.paymentservice.entity.PaymentStatus;

import java.util.Set;

final class StripePaymentStatusMapper {

    // Only a fully captured PaymentIntent (succeeded) counts as successful.
    // requires_capture authorizes funds but does not collect them.
    private static final Set<String> SUCCESSFUL_STATUSES =
            Set.of("succeeded");

    private StripePaymentStatusMapper() {
    }

    static PaymentStatus toPaymentStatus(String stripeStatus) {
        return SUCCESSFUL_STATUSES.contains(stripeStatus)
                ? PaymentStatus.SUCCESSFUL
                : PaymentStatus.UNSUCCESSFUL;
    }
}