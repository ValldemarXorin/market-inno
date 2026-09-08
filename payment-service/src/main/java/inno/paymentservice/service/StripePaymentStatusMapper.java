package inno.paymentservice.service;

import inno.paymentservice.entity.PaymentStatus;

import java.util.Set;

/**
 * Stripe PaymentIntent statuses to the domain {@link PaymentStatus}.
 *
 * <p>succeeded and requires_capture (funds authorized) are final for this flow. The intermediate
 * statuses (requires_payment_method, requires_confirmation, requires_action, processing, canceled)
 * cannot be represented by the two-value domain model in a synchronous flow, so they are mapped to
 * {@link PaymentStatus#UNSUCCESSFUL}: an incomplete payment is never reported as successful.
 */
final class StripePaymentStatusMapper {

    private static final Set<String> SUCCESSFUL_STATUSES =
            Set.of("succeeded", "requires_capture");

    private StripePaymentStatusMapper() {
    }

    static PaymentStatus toPaymentStatus(String stripeStatus) {
        return SUCCESSFUL_STATUSES.contains(stripeStatus)
                ? PaymentStatus.SUCCESSFUL
                : PaymentStatus.UNSUCCESSFUL;
    }
}