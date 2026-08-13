package inno.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID itemId,
        String itemName,
        BigDecimal price,
        Integer quantity
) {
}