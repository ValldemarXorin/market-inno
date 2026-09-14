package inno.paymentservice.client;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StripePaymentClient {

    private final String secretKey;

    public StripePaymentClient(@Value("${stripe.secret-key}") String secretKey) {
        this.secretKey = secretKey;
        validateTestModeKey(secretKey);
    }

    public PaymentIntent createAndConfirmPaymentIntent(
            BigDecimal amount, String currencyCode, UUID orderId, String idempotencyKey)
            throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", toMinorUnit(amount));
        params.put("currency", currencyCode);
        params.put("confirm", true);
        params.put("description", "Payment for order " + orderId);
        params.put("metadata", Map.of("orderId", orderId.toString()));

        RequestOptions options = RequestOptions.builder()
                .setApiKey(secretKey)
                .setIdempotencyKey(idempotencyKey)
                .build();
        return PaymentIntent.create(params, options);
    }

    private void validateTestModeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Stripe key must be a TEST MODE key (sk_test_...)");
        }
    }

    private long toMinorUnit(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}