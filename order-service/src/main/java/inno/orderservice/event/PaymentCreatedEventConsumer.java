package inno.orderservice.event;

import inno.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedEventConsumer.class);

    private final OrderService orderService;

    @KafkaListener(topics = "${app.kafka.topic.payment-created:payment-created-events}")
    public void onPaymentCreated(@Header(KafkaHeaders.RECEIVED_KEY) String paymentId,
                                 @Payload PaymentCreatedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Payment created event payload is null");
        }

        UUID keyPaymentId = UUID.fromString(paymentId);
        if (!keyPaymentId.equals(event.paymentId())) {
            throw new IllegalArgumentException(
                    "Kafka key does not match event paymentId: key=" + paymentId + ", event=" + event.paymentId());
        }

        log.info("Received payment created event: paymentId={}, orderId={}, status={}",
                event.paymentId(), event.orderId(), event.status());
        orderService.processPayment(event);
    }
}