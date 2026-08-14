package inno.orderservice.controller;

import inno.orderservice.client.UserServiceClient;
import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.UpdateOrderRequest;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.mapper.OrderMapper;
import inno.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        OrderResponse orderResponseCreated = orderService.createOrder(createOrderRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + orderResponseCreated.id()))
                .body(enrich(orderResponseCreated));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN') or @resourceSecurity.isOrderOwner(authentication, #id)")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(enrich(orderService.getOrderById(id)));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<OrderStatus> statuses,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrders(from, to, statuses, pageable)
                .map(this::enrich));
    }

    @GetMapping("/users/{userId}/orders")
    @PreAuthorize("hasRole('ADMIN') or @resourceSecurity.isSelf(authentication, #userId)")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable UUID userId,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId, pageable)
                .map(this::enrich));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN') or @resourceSecurity.isOrderOwner(authentication, #id)")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest updateOrderRequest) {
        return ResponseEntity.ok(enrich(orderService.updateOrder(id, updateOrderRequest)));
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    private OrderResponse enrich(OrderResponse order) {
        return orderMapper.toResponse(order, userServiceClient.getUserByEmail(order.email()));
    }
}