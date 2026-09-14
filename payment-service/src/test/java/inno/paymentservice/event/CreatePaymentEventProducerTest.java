package inno.paymentservice.event;

import inno.paymentservice.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreatePaymentEventProducerTest {

    private static final String TOPIC = "create-payment-events";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CreatePaymentEventProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "createPaymentTopic", TOPIC);
    }

    @Test
    void shouldSendEventWithPaymentIdAsKey() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CreatePaymentEvent event = new CreatePaymentEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        producer.publish(event);

        ArgumentCaptor<CreatePaymentEvent> captor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(paymentId.toString()), captor.capture());
        assertEquals(paymentId, captor.getValue().paymentId());
        assertEquals(orderId, captor.getValue().orderId());
        assertEquals(PaymentStatus.SUCCESSFUL, captor.getValue().status());
    }
}