package inno.orderservice.controller;

import inno.orderservice.client.UserServiceClient;
import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.OrderFilterRequest;
import inno.orderservice.dto.request.UpdateOrderRequest;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.mapper.OrderMapper;
import inno.orderservice.security.util.ResourceSecurityService;
import inno.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserServiceClient userServiceClient;
    private final OrderMapper orderMapper;
    private final ResourceSecurityService resourceSecurity;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        OrderResponse orderResponseCreated = orderService.createOrder(createOrderRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + orderResponseCreated.id()))
                .body(enrich(orderResponseCreated));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        resourceSecurity.requireAdminOrOrderOwner(id);
        return ResponseEntity.ok(enrich(orderService.getOrderById(id)));
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @ModelAttribute OrderFilterRequest filter,
            Pageable pageable) {

        return ResponseEntity.ok(
                orderService.getOrders(filter, pageable)
                        .map(this::enrich)
        );
    }

    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable UUID userId,
            Pageable pageable) {
        resourceSecurity.requireAdminOrSelf(userId);
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId, pageable)
                .map(this::enrich));
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest updateOrderRequest) {
        resourceSecurity.requireAdminOrOrderOwner(id);
        return ResponseEntity.ok(enrich(orderService.updateOrder(id, updateOrderRequest)));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        resourceSecurity.requireAdmin();
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    private OrderResponse enrich(OrderResponse order) {
        return orderMapper.toResponse(order, userServiceClient.getUserByEmail(order.email()));
    }
}