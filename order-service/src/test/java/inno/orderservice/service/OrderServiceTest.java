package inno.orderservice.service;

import inno.orderservice.dao.repository.ItemRepository;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.OrderItemRequest;
import inno.orderservice.dto.request.UpdateOrderRequest;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.Item;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderItem;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.exception.custom_exception.ItemNotFoundException;
import inno.orderservice.exception.custom_exception.OrderNotFoundException;
import inno.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private UUID testOrderId;
    private UUID testUserId;
    private UUID testItemId;
    private Item testItem;
    private Order testOrder;
    private OrderItem testOrderItem;
    private OrderResponse testOrderResponse;

    @BeforeEach
    public void initData() {
        testOrderId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testItemId = UUID.randomUUID();

        testItem = new Item();
        testItem.setId(testItemId);
        testItem.setName("testItem");
        testItem.setPrice(new BigDecimal("10.00"));

        testOrderItem = new OrderItem();
        testOrderItem.setItem(testItem);
        testOrderItem.setQuantity(3);

        testOrder = new Order();
        testOrder.setId(testOrderId);
        testOrder.setUserId(testUserId);
        testOrder.setStatus(OrderStatus.CREATED);
        testOrder.setTotalPrice(new BigDecimal("30.00"));

        testOrderResponse = new OrderResponse(
                testOrderId, testUserId, OrderStatus.CREATED,
                new BigDecimal("30.00"), false, List.of(), null);
    }

    @Test
    public void shouldCreateOrderSuccessfully() {
        CreateOrderRequest request = new CreateOrderRequest(
                testUserId, List.of(new OrderItemRequest(testItemId, 3)));

        when(itemRepository.findById(testItemId)).thenReturn(Optional.of(testItem));
        when(orderMapper.toEntity(any(CreateOrderRequest.class))).thenReturn(testOrder);
        when(orderMapper.toEntity(any(OrderItemRequest.class))).thenReturn(testOrderItem);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(testOrderResponse);

        OrderResponse response = orderService.createOrder(request);

        assertEquals(testOrderResponse, response);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertSame(testOrder, saved);
        assertEquals(OrderStatus.CREATED, saved.getStatus());
        assertEquals(new BigDecimal("30.00"), saved.getTotalPrice());
        assertEquals(1, saved.getOrderItems().size());

        OrderItem savedItem = saved.getOrderItems().get(0);
        assertSame(saved, savedItem.getOrder());
        assertSame(testItem, savedItem.getItem());
        assertEquals(3, savedItem.getQuantity());

        verify(itemRepository, never()).save(any());
    }

    @Test
    public void shouldThrowItemNotFoundExceptionWhenItemIsMissing() {
        CreateOrderRequest request = new CreateOrderRequest(
                testUserId, List.of(new OrderItemRequest(testItemId, 1)));

        when(itemRepository.findById(testItemId)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    public void shouldGetOrderById() {
        when(orderRepository.findByIdAndDeletedFalse(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderMapper.toResponse(testOrder)).thenReturn(testOrderResponse);

        OrderResponse response = orderService.getOrderById(testOrderId);

        assertEquals(testOrderResponse, response);
    }

    @Test
    public void shouldThrowOrderNotFoundExceptionWhenOrderIsDeletedOrMissing() {
        when(orderRepository.findByIdAndDeletedFalse(testOrderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(testOrderId));
    }

    @Test
    public void shouldGetOrdersWithPaginationAndFilters() {
        LocalDateTime from = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);
        Page<Order> page = new PageImpl<>(List.of(testOrder));

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponse(testOrder)).thenReturn(testOrderResponse);

        Page<OrderResponse> result = orderService.getOrders(
                from, to, List.of(OrderStatus.CREATED), Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals(testOrderResponse, result.getContent().get(0));
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void shouldGetOrdersByUserId() {
        Page<Order> page = new PageImpl<>(List.of(testOrder));

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponse(testOrder)).thenReturn(testOrderResponse);

        Page<OrderResponse> result = orderService.getOrdersByUserId(testUserId, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals(testOrderResponse, result.getContent().get(0));
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void shouldUpdateOrder() {
        testOrder.getOrderItems().add(testOrderItem);
        UpdateOrderRequest request = new UpdateOrderRequest(
                OrderStatus.PROCESSING, List.of(new OrderItemRequest(testItemId, 2)));
        testOrderItem.setQuantity(2);

        when(orderRepository.findByIdAndDeletedFalse(testOrderId)).thenReturn(Optional.of(testOrder));
        when(itemRepository.findById(testItemId)).thenReturn(Optional.of(testItem));
        when(orderMapper.toEntity(any(OrderItemRequest.class))).thenReturn(testOrderItem);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(testOrderResponse);

        orderService.updateOrder(testOrderId, request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertEquals(OrderStatus.PROCESSING, saved.getStatus());
        assertEquals(new BigDecimal("20.00"), saved.getTotalPrice());
        assertEquals(1, saved.getOrderItems().size());

        OrderItem savedItem = saved.getOrderItems().get(0);
        assertSame(saved, savedItem.getOrder());
        assertSame(testItem, savedItem.getItem());
        assertEquals(2, savedItem.getQuantity());
    }

    @Test
    public void shouldThrowOrderNotFoundExceptionWhenUpdatingDeletedOrder() {
        when(orderRepository.findByIdAndDeletedFalse(testOrderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.updateOrder(
                testOrderId, new UpdateOrderRequest(OrderStatus.CANCELLED, List.of())));
    }

    @Test
    public void shouldSoftDeleteOrderWithoutPhysicalDelete() {
        when(orderRepository.softDeleteById(testOrderId)).thenReturn(1);

        orderService.deleteOrder(testOrderId);

        verify(orderRepository).softDeleteById(testOrderId);
        verify(orderRepository, never()).delete((Order) any());
        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    public void shouldThrowOrderNotFoundExceptionWhenDeletingDeletedOrder() {
        when(orderRepository.softDeleteById(testOrderId)).thenReturn(0);

        assertThrows(OrderNotFoundException.class, () -> orderService.deleteOrder(testOrderId));
    }
}