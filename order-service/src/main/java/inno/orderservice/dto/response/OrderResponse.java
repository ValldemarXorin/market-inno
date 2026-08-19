package inno.orderservice.dto.response;

import inno.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String email,
        OrderStatus status,
        BigDecimal totalPrice,
        Boolean deleted,
        List<OrderItemResponse> items,
        UserResponse user
) {
}