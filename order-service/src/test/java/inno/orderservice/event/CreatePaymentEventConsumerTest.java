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
class CreatePaymentEventConsumerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private CreatePaymentEventConsumer consumer;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void shouldDelegateToOrderServiceWhenKeyMatches() {
        CreatePaymentEvent event = new CreatePaymentEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        consumer.onCreatePayment(paymentId.toString(), event);

        verify(orderService).processPayment(event);
    }

    @Test
    void shouldRejectEventWhenKeyDoesNotMatchPaymentId() {
        CreatePaymentEvent event = new CreatePaymentEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.onCreatePayment(UUID.randomUUID().toString(), event));

        verify(orderService, never()).processPayment(any());
    }

    @Test
    void shouldRejectEventWhenKeyIsNotValidUuid() {
        CreatePaymentEvent event = new CreatePaymentEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.onCreatePayment("not-a-uuid", event));

        verify(orderService, never()).processPayment(any());
    }

    @Test
    void shouldRejectNullEventPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> consumer.onCreatePayment(paymentId.toString(), null));

        verify(orderService, never()).processPayment(any());
    }
}