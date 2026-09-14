package inno.paymentservice.event;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.create-payment:create-payment-events}")
    private String createPaymentTopic;

    public void publish(CreatePaymentEvent event) {
        log.info("Publishing create payment event: paymentId={}, orderId={}, status={}",
                event.paymentId(), event.orderId(), event.status());
        kafkaTemplate.send(createPaymentTopic, event.paymentId().toString(), event);
    }
}