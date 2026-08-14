package inno.orderservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payment-created-events"})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

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
    void invalidJwtShouldBeRejected() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/orders").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminShouldAccessProtectedOrderEndpoints() throws Exception {
        UUID adminId = UUID.randomUUID();
        Order order = createOrder(adminId);

        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + TestTokens.token(adminId, "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders/" + order.getId())
                        .header("Authorization", "Bearer " + TestTokens.token(adminId, "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/orders/" + order.getId())
                        .header("Authorization", "Bearer " + TestTokens.token(adminId, "ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void userShouldAccessOwnOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        Order order = createOrder(userId);

        mockMvc.perform(get("/orders/" + order.getId())
                        .header("Authorization", "Bearer " + TestTokens.userToken(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void userShouldBeDeniedAdminEndpointsAndOtherUsersOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order ownOrder = createOrder(userId);
        Order otherOrder = createOrder(otherUserId);

        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + TestTokens.userToken(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/" + otherUserId + "/orders")
                        .header("Authorization", "Bearer " + TestTokens.userToken(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/orders/" + otherOrder.getId())
                        .header("Authorization", "Bearer " + TestTokens.userToken(userId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/orders/" + ownOrder.getId())
                        .header("Authorization", "Bearer " + TestTokens.userToken(userId)))
                .andExpect(status().isForbidden());
    }

    private Order createOrder(UUID userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setUserEmail("vova@gmail.com");
        order.setStatus(OrderStatus.CREATED);
        order.setTotalPrice(new BigDecimal("30.00"));
        return orderRepository.save(order);
    }
}