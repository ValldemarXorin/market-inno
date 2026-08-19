package inno.orderservice.mapper;

import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.OrderItemRequest;
import inno.orderservice.dto.response.OrderItemResponse;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.dto.response.UserResponse;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "userEmail", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(CreateOrderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "item", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrderItem toEntity(OrderItemRequest request);

    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "email", source = "userEmail")
    @Mapping(target = "user", ignore = true)
    OrderResponse toResponse(Order order);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "price", source = "item.price")
    OrderItemResponse toResponse(OrderItem orderItem);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "email", source = "order.email")
    @Mapping(target = "user", source = "user")
    OrderResponse toResponse(OrderResponse order, UserResponse user);
}