package inno.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import inno.orderservice.dao.repository.ItemRepository;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.Item;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.security.CurrentUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payment-created-events"})
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

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

        registry.add("app.user-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/users/by-email/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"c0a8d1e2-0000-0000-0000-000000000001","username":"vova","surname":"khorin",
                                 "birthDate":"2006-01-20","email":"vova@gmail.com","active":true,
                                 "createdAt":"2026-01-01T12:00:00","updatedAt":"2026-01-01T12:00:00"}
                                """)));
    }

    @Test
    void unauthenticatedRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/orders/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/users/00000000-0000-0000-0000-000000000000/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidIdentityShouldBeRejected() throws Exception {
        mockMvc.perform(get("/orders").header(CurrentUser.USER_ID_HEADER, "not-a-uuid")
                        .header(CurrentUser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/orders").header(CurrentUser.USER_ID_HEADER, UUID.randomUUID().toString())
                        .header(CurrentUser.USER_ROLE_HEADER, "ROOT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminShouldAccessProtectedOrderEndpoints() throws Exception {
        UUID adminId = UUID.randomUUID();
        Order order = createOrder(adminId);

        mockMvc.perform(get("/orders").headers(TestIdentity.adminHeaders()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders/" + order.getId())
                        .headers(TestIdentity.adminHeaders()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + adminId + "/orders")
                        .headers(TestIdentity.adminHeaders()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/orders/" + order.getId())
                        .headers(TestIdentity.adminHeaders()))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminShouldCreateAndUpdateOrder() throws Exception {
        UUID adminId = UUID.randomUUID();
        Item item = createItem();

        String created = mockMvc.perform(post("/orders")
                        .headers(TestIdentity.adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"vova@gmail.com","items":[{"itemId":"%s","quantity":3}]}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse createdOrder = objectMapper.readValue(created, OrderResponse.class);

        mockMvc.perform(put("/orders/" + createdOrder.id())
                        .headers(TestIdentity.adminHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PROCESSING","items":[{"itemId":"%s","quantity":2}]}
                                """.formatted(item.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void userShouldAccessOwnOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        Order order = createOrder(userId);

        mockMvc.perform(get("/orders/" + order.getId())
                        .headers(TestIdentity.userHeaders(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void userShouldBeDeniedAdminEndpointsAndOtherUsersOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order ownOrder = createOrder(userId);
        Order otherOrder = createOrder(otherUserId);

        mockMvc.perform(get("/orders").headers(TestIdentity.userHeaders(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/" + otherUserId + "/orders")
                        .headers(TestIdentity.userHeaders(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/orders/" + otherOrder.getId())
                        .headers(TestIdentity.userHeaders(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/orders/" + ownOrder.getId())
                        .headers(TestIdentity.userHeaders(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void identityHeadersShouldBeForwardedToUserService() throws Exception {
        UUID adminId = UUID.randomUUID();
        Order order = createOrder(adminId);

        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/users/by-email/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"c0a8d1e2-0000-0000-0000-000000000001","username":"vova","surname":"khorin",
                                 "birthDate":"2006-01-20","email":"vova@gmail.com","active":true,
                                 "createdAt":"2026-01-01T12:00:00","updatedAt":"2026-01-01T12:00:00"}
                                """)));

        mockMvc.perform(get("/orders/" + order.getId())
                        .headers(TestIdentity.identityHeaders(adminId, "ADMIN")))
                .andExpect(status().isOk());

        assertEquals(1, wireMockServer.getAllServeEvents().size());
        var forwarded = wireMockServer.getAllServeEvents().get(0).getRequest().getHeaders();
        assertEquals(adminId.toString(), forwarded.getHeader(CurrentUser.USER_ID_HEADER).firstValue());
        assertEquals("ADMIN", forwarded.getHeader(CurrentUser.USER_ROLE_HEADER).firstValue());
    }

    private Order createOrder(UUID userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setUserEmail("vova@gmail.com");
        order.setStatus(OrderStatus.CREATED);
        order.setTotalPrice(new BigDecimal("30.00"));
        return orderRepository.save(order);
    }

    private Item createItem() {
        Item item = new Item();
        item.setName("test item");
        item.setPrice(new BigDecimal("10.00"));
        return itemRepository.save(item);
    }
}