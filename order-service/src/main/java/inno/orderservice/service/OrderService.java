package inno.orderservice.service;

import inno.orderservice.dao.repository.ItemRepository;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.dao.specification.OrderSpecification;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        List<OrderItem> items = resolveItems(request.items(), order);
        order.getOrderItems().addAll(items);
        order.setTotalPrice(calculateTotal(items));
        return orderMapper.toResponse(orderRepository.save(order));
    }

    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return orderMapper.toResponse(order);
    }

    public Page<OrderResponse> getOrders(LocalDateTime from, LocalDateTime to,
                                         List<OrderStatus> statuses, Pageable pageable) {
        return orderRepository.findAll(
                        OrderSpecification.notDeleted()
                                .and(OrderSpecification.createdBetween(from, to))
                                .and(OrderSpecification.hasStatuses(statuses)),
                        pageable)
                .map(orderMapper::toResponse);
    }

    public Page<OrderResponse> getOrdersByUserId(UUID userId, Pageable pageable) {
        return orderRepository.findAll(
                        OrderSpecification.notDeleted()
                                .and(OrderSpecification.hasUserId(userId)),
                        pageable)
                .map(orderMapper::toResponse);
    }

    @Transactional
    public OrderResponse updateOrder(UUID id, UpdateOrderRequest request) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        List<OrderItem> updatedItems = resolveItems(request.items(), order);

        order.setStatus(request.status());
        order.getOrderItems().clear();
        order.getOrderItems().addAll(updatedItems);
        order.setTotalPrice(calculateTotal(updatedItems));

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public void deleteOrder(UUID id) {
        int updated = orderRepository.softDeleteById(id);
        if (updated == 0) {
            throw new OrderNotFoundException(id);
        }
    }

    private List<OrderItem> resolveItems(List<OrderItemRequest> requests, Order order) {
        return requests.stream()
                .map(request -> {
                    Item item = itemRepository.findById(request.itemId())
                            .orElseThrow(() -> new ItemNotFoundException(request.itemId()));
                    OrderItem orderItem = orderMapper.toEntity(request);
                    orderItem.setItem(item);
                    orderItem.setOrder(order);
                    return orderItem;
                })
                .toList();
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(orderItem -> orderItem.getItem().getPrice()
                        .multiply(BigDecimal.valueOf(orderItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}