package inno.orderservice.controller;

import inno.orderservice.client.UserServiceClient;
import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.UpdateOrderRequest;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.dto.response.UserResponse;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.mapper.OrderMapper;
import inno.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderController orderController;

    private UUID testOrderId;
    private UUID testUserId;
    private String testEmail;
    private OrderResponse orderWithoutUser;
    private OrderResponse orderWithUser;
    private UserResponse user;

    @BeforeEach
    void initData() {
        testOrderId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testEmail = "vova@gmail.com";

        orderWithoutUser = new OrderResponse(
                testOrderId, testUserId, testEmail, OrderStatus.CREATED,
                new BigDecimal("30.00"), false, List.of(), null);

        user = new UserResponse(
                testUserId, "vova", "khorin", LocalDate.of(2006, 1, 20),
                testEmail, true, null, null);

        orderWithUser = new OrderResponse(
                testOrderId, testUserId, testEmail, OrderStatus.CREATED,
                new BigDecimal("30.00"), false, List.of(), user);
    }

    @Test
    void shouldCreateOrderAndEnrichWithUserByEmail() {
        CreateOrderRequest request = new CreateOrderRequest(testEmail, List.of());

        when(orderService.createOrder(request)).thenReturn(orderWithoutUser);
        when(userServiceClient.getUserByEmail(testEmail)).thenReturn(user);
        when(orderMapper.toResponse(orderWithoutUser, user)).thenReturn(orderWithUser);

        ResponseEntity<OrderResponse> response = orderController.createOrder(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/orders/" + testOrderId, response.getHeaders().getLocation().toString());
        assertSame(orderWithUser, response.getBody());
        assertNotNull(response.getBody().user());
        verify(userServiceClient).getUserByEmail(testEmail);
    }

    @Test
    void shouldGetOrderByIdWithUserByEmail() {
        when(orderService.getOrderById(testOrderId)).thenReturn(orderWithoutUser);
        when(userServiceClient.getUserByEmail(testEmail)).thenReturn(user);
        when(orderMapper.toResponse(orderWithoutUser, user)).thenReturn(orderWithUser);

        ResponseEntity<OrderResponse> response = orderController.getOrderById(testOrderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(orderWithUser, response.getBody());
        assertNotNull(response.getBody().user());
        verify(userServiceClient).getUserByEmail(testEmail);
    }

    @Test
    void shouldGetOrdersWithPaginationEnrichedWithUser() {
        Page<OrderResponse> page = new PageImpl<>(List.of(orderWithoutUser));

        when(orderService.getOrders(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(userServiceClient.getUserByEmail(testEmail)).thenReturn(user);
        when(orderMapper.toResponse(orderWithoutUser, user)).thenReturn(orderWithUser);

        ResponseEntity<Page<OrderResponse>> response =
                orderController.getOrders(null, null, null, Pageable.unpaged());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertNotNull(response.getBody().getContent().get(0).user());
    }

    @Test
    void shouldGetOrdersByUserIdWithUser() {
        Page<OrderResponse> page = new PageImpl<>(List.of(orderWithoutUser));

        when(orderService.getOrdersByUserId(testUserId, Pageable.unpaged())).thenReturn(page);
        when(userServiceClient.getUserByEmail(testEmail)).thenReturn(user);
        when(orderMapper.toResponse(orderWithoutUser, user)).thenReturn(orderWithUser);

        ResponseEntity<Page<OrderResponse>> response =
                orderController.getOrdersByUserId(testUserId, Pageable.unpaged());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getContent().get(0).user());
    }

    @Test
    void shouldUpdateOrderAndEnrichWithUser() {
        UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.PROCESSING, List.of());

        when(orderService.updateOrder(testOrderId, request)).thenReturn(orderWithoutUser);
        when(userServiceClient.getUserByEmail(testEmail)).thenReturn(user);
        when(orderMapper.toResponse(orderWithoutUser, user)).thenReturn(orderWithUser);

        ResponseEntity<OrderResponse> response = orderController.updateOrder(testOrderId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(orderWithUser, response.getBody());
        assertNotNull(response.getBody().user());
    }

    @Test
    void shouldDeleteOrderWithoutUserInformation() {
        ResponseEntity<Void> response = orderController.deleteOrder(testOrderId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(orderService).deleteOrder(testOrderId);
        verifyNoInteractions(userServiceClient, orderMapper);
    }
}