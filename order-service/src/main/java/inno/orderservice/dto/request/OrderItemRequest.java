package inno.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "Item id must not be null")
        UUID itemId,

        @NotNull(message = "Quantity must not be null")
        @Min(value = 1, message = "Quantity must be positive")
        Integer quantity
) {
}