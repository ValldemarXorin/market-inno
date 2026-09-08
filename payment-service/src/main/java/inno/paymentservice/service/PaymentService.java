package inno.paymentservice.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import inno.paymentservice.client.StripePaymentClient;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.dto.response.TotalResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import inno.paymentservice.event.CreatePaymentEventProducer;
import inno.paymentservice.exception.custom_exception.StripePaymentException;
import inno.paymentservice.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final StripePaymentClient stripePaymentClient;
    private final CreatePaymentEventProducer createPaymentEventProducer;

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        PaymentIntent paymentIntent = createStripePaymentIntent(request);
        PaymentStatus status = StripePaymentStatusMapper.toPaymentStatus(paymentIntent.getStatus());

        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(status);
        payment.setStripePaymentIntentId(paymentIntent.getId());

        Payment saved = paymentRepository.save(payment);
        createPaymentEventProducer.publish(
                new CreatePaymentEvent(saved.getId(), saved.getOrderId(), saved.getStatus()));
        return paymentMapper.toResponse(saved);
    }

    public List<PaymentResponse> getPaymentsByUserId(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public TotalResponse getTotalForUser(UUID userId, LocalDateTime start, LocalDateTime end) {
        return totalResponse(
                paymentRepository.findTotalByUserIdAndTimestampBetween(userId, start, end));
    }

    public TotalResponse getTotalForAllUsers(LocalDateTime start, LocalDateTime end) {
        return totalResponse(
                paymentRepository.findTotalByTimestampBetween(start, end));
    }

    private PaymentIntent createStripePaymentIntent(CreatePaymentRequest request) {
        // Idempotency key is derived from order id, so a retried operation cannot create a
        // duplicate Stripe charge: Stripe replays the original PaymentIntent for the key.
        try {
            return stripePaymentClient.createAndConfirmPaymentIntent(
                    request.paymentAmount(),
                    request.currency().getStripeCode(),
                    request.orderId(),
                    "payment-order-" + request.orderId());
        } catch (StripeException ex) {
            throw new StripePaymentException(
                    "Stripe payment failed for order " + request.orderId() + ": " + ex.getMessage(), ex);
        }
    }

    private TotalResponse totalResponse(List<PaymentRepository.Total> totals) {
        if (totals == null || totals.isEmpty()) {
            return new TotalResponse(BigDecimal.ZERO);
        }
        return new TotalResponse(BigDecimal.valueOf(totals.get(0).getTotal()));
    }
}