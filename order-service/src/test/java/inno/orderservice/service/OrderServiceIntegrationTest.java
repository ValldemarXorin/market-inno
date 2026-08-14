package inno.orderservice.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import inno.orderservice.TestTokens;
import inno.orderservice.dao.repository.ItemRepository;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.dto.request.CreateOrderRequest;
import inno.orderservice.dto.request.OrderItemRequest;
import inno.orderservice.dto.request.UpdateOrderRequest;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.Item;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.exception.custom_exception.OrderNotFoundException;
import inno.orderservice.exception.custom_exception.UserNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payment-created-events"})
@Transactional
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("orderservice")
            .withUsername("postgres")
            .withPassword("postgres");

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);

        registry.add("app.user-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    private static final String EMAIL = "vova@gmail.com";
    private static final UUID USER_ID = UUID.fromString("c0a8d1e2-0000-0000-0000-000000000001");

    private Item item;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        item = new Item();
        item.setName("test item");
        item.setPrice(new BigDecimal("10.00"));
        item = itemRepository.save(item);
    }

    @Test
    void shouldCreateOrderPersistingToPostgres() {
        stubUserOk(EMAIL, USER_ID);

        OrderResponse created = orderService.createOrder(
                new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(item.getId(), 3))));

        assertNotNull(created.id());
        assertEquals(USER_ID, created.userId());
        assertEquals(EMAIL, created.email());
        assertEquals(OrderStatus.CREATED, created.status());
        assertEquals(new BigDecimal("30.00"), created.totalPrice());
        assertEquals(1, created.items().size());

        Order persisted = orderRepository.findById(created.id()).orElseThrow();
        assertEquals(created.id(), persisted.getId());
        assertEquals(USER_ID, persisted.getUserId());
        assertEquals(EMAIL, persisted.getUserEmail());
        assertEquals(OrderStatus.CREATED, persisted.getStatus());
        assertEquals(new BigDecimal("30.00"), persisted.getTotalPrice());
        assertEquals(1, persisted.getOrderItems().size());
    }

    @Test
    void shouldGetOrderByIdFromDatabase() {
        stubUserOk(EMAIL, USER_ID);
        OrderResponse created = createOrder();

        OrderResponse fetched = orderService.getOrderById(created.id());

        assertEquals(created.id(), fetched.id());
        assertEquals(EMAIL, fetched.email());
        assertEquals(OrderStatus.CREATED, fetched.status());
        assertEquals(new BigDecimal("30.00"), fetched.totalPrice());
        assertEquals(1, fetched.items().size());
    }

    @Test
    void shouldQueryOrdersByStatusAndUserId() {
        stubUserOk(EMAIL, USER_ID);
        OrderResponse first = createOrder();
        createOrder();

        Page<OrderResponse> allCreated = orderService.getOrders(
                null, null, List.of(OrderStatus.CREATED), Pageable.unpaged());
        assertEquals(2, allCreated.getTotalElements());

        Page<OrderResponse> byUser = orderService.getOrdersByUserId(USER_ID, Pageable.unpaged());
        assertEquals(2, byUser.getTotalElements());
        assertTrue(byUser.getContent().stream().anyMatch(order -> order.id().equals(first.id())));

        Page<OrderResponse> byUnmatchedStatus = orderService.getOrders(
                null, null, List.of(OrderStatus.COMPLETED), Pageable.unpaged());
        assertEquals(0, byUnmatchedStatus.getTotalElements());
    }

    @Test
    void shouldUpdateOrderStatusAndItems() {
        stubUserOk(EMAIL, USER_ID);
        OrderResponse created = createOrder();

        OrderResponse updated = orderService.updateOrder(created.id(),
                new UpdateOrderRequest(OrderStatus.PROCESSING,
                        List.of(new OrderItemRequest(item.getId(), 2))));

        assertEquals(OrderStatus.PROCESSING, updated.status());
        assertEquals(new BigDecimal("20.00"), updated.totalPrice());

        Order persisted = orderRepository.findById(created.id()).orElseThrow();
        assertEquals(OrderStatus.PROCESSING, persisted.getStatus());
        assertEquals(new BigDecimal("20.00"), persisted.getTotalPrice());
        assertEquals(1, persisted.getOrderItems().size());
    }

    @Test
    void shouldSoftDeleteOrder() {
        stubUserOk(EMAIL, USER_ID);
        OrderResponse created = createOrder();

        orderService.deleteOrder(created.id());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(created.id()));
        Order deleted = orderRepository.findById(created.id()).orElseThrow();
        assertTrue(deleted.getDeleted());
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserIsNotFound() {
        stubUserNotFound(EMAIL);

        assertThrows(UserNotFoundException.class, () -> orderService.createOrder(
                new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(item.getId(), 1)))));
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserServiceFails() {
        stubUserServerError(EMAIL);

        assertThrows(UserNotFoundException.class, () -> orderService.createOrder(
                new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(item.getId(), 1)))));
    }

    private OrderResponse createOrder() {
        return orderService.createOrder(new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(item.getId(), 3))));
    }

    private static void stubUserOk(String email, UUID userId) {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(email, userId))));
    }

    private static void stubUserNotFound(String email) {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"not found\"}")));
    }

    private static void stubUserServerError(String email) {
        wireMockServer.stubFor(get(urlPathMatching(userUrlPattern()))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    private static String userUrlPattern() {
        return "/users/by-email/.*";
    }

    private static String userJson(String email, UUID userId) {
        return """
                {"id":"%s","username":"vova","surname":"khorin",
                 "birthDate":"2006-01-20","email":"%s","active":true,
                 "createdAt":"2026-01-01T12:00:00","updatedAt":"2026-01-01T12:00:00"}
                """.formatted(userId, email);
    }
}