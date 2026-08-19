package inno.orderservice.event;

import inno.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCreatedEventConsumerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentCreatedEventConsumer consumer;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void shouldDelegateToOrderServiceWhenKeyMatches() {
        PaymentCreatedEvent event = new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        consumer.onPaymentCreated(paymentId.toString(), event);

        verify(orderService).processPayment(event);
    }

    @Test
    void shouldRejectEventWhenKeyDoesNotMatchPaymentId() {
        PaymentCreatedEvent event = new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.onPaymentCreated(UUID.randomUUID().toString(), event));

        verify(orderService, never()).processPayment(any());
    }

    @Test
    void shouldRejectEventWhenKeyIsNotValidUuid() {
        PaymentCreatedEvent event = new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.onPaymentCreated("not-a-uuid", event));

        verify(orderService, never()).processPayment(any());
    }

    @Test
    void shouldRejectNullEventPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> consumer.onPaymentCreated(paymentId.toString(), null));

        verify(orderService, never()).processPayment(any());
    }
}