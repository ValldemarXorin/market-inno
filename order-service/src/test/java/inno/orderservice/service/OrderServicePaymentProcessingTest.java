package inno.orderservice.service;

import inno.orderservice.client.UserServiceClient;
import inno.orderservice.dao.repository.ItemRepository;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.entity.Item;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderItem;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.event.PaymentCreatedEvent;
import inno.orderservice.event.PaymentStatus;
import inno.orderservice.exception.custom_exception.OrderNotFoundException;
import inno.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServicePaymentProcessingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderService orderService;

    private UUID testOrderId;
    private UUID testPaymentId;
    private Order testOrder;

    @BeforeEach
    void initData() {
        testOrderId = UUID.randomUUID();
        testPaymentId = UUID.randomUUID();

        testOrder = new Order();
        testOrder.setId(testOrderId);
        testOrder.setUserId(UUID.randomUUID());
        testOrder.setUserEmail("vova@gmail.com");
        testOrder.setStatus(OrderStatus.CREATED);
        testOrder.setTotalPrice(new BigDecimal("30.00"));
    }

    @Test
    void shouldMarkOrderCompletedOnSuccessfulPayment() {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.processPayment(new PaymentCreatedEvent(testPaymentId, testOrderId, PaymentStatus.SUCCESSFUL));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.COMPLETED, orderCaptor.getValue().getStatus());
    }

    @Test
    void shouldCancelOrderOnUnsuccessfulPayment() {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.processPayment(new PaymentCreatedEvent(testPaymentId, testOrderId, PaymentStatus.UNSUCCESSFUL));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.CANCELLED, orderCaptor.getValue().getStatus());
    }

    @Test
    void shouldSkipDuplicateSuccessfulPayment() {
        testOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        orderService.processPayment(new PaymentCreatedEvent(testPaymentId, testOrderId, PaymentStatus.SUCCESSFUL));

        verify(orderRepository, never()).save(any());
        assertEquals(OrderStatus.COMPLETED, testOrder.getStatus());
    }

    @Test
    void shouldSkipDuplicateUnsuccessfulPayment() {
        testOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        orderService.processPayment(new PaymentCreatedEvent(testPaymentId, testOrderId, PaymentStatus.UNSUCCESSFUL));

        verify(orderRepository, never()).save(any());
        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
    }

    @Test
    void shouldThrowOrderNotFoundExceptionWhenOrderIsMissing() {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.processPayment(
                new PaymentCreatedEvent(testPaymentId, testOrderId, PaymentStatus.SUCCESSFUL)));
    }
}