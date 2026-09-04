package inno.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
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
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"create-payment-events"})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("paymentservice")
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

        registry.add("app.random-number-api.base-url", () -> "http://localhost:" + wireMockServer.port());
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
    private PaymentRepository paymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathMatching("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"number\":4}")));
    }

    @Test
    void shouldCreatePaymentPersistingToPostgres() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String responseContent = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":100.00}
                                """.formatted(orderId, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$.paymentAmount").value(100.00))
                .andReturn().getResponse().getContentAsString();

        PaymentResponse created = objectMapper.readValue(responseContent, PaymentResponse.class);
        assertNotNull(created.id());

        Payment persisted = paymentRepository.findById(created.id()).orElseThrow();
        assertEquals(created.id(), persisted.getId());
        assertEquals(orderId, persisted.getOrderId());
        assertEquals(userId, persisted.getUserId());
        assertEquals(PaymentStatus.SUCCESSFUL, persisted.getStatus());
        assertEquals(new BigDecimal("100.00"), persisted.getPaymentAmount());
        assertEquals(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0), persisted.getTimestamp());
    }

    @Test
    void shouldReturnBadRequestForInvalidPaymentRequest() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":null,"userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":-5}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateUnsuccessfulPaymentWhenRandomNumberIsOdd() throws Exception {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathMatching("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"number\":5}")));

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":50.00}
                                """.formatted(orderId, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNSUCCESSFUL"));

        assertTrue(paymentRepository.findAll().stream()
                .anyMatch(payment -> payment.getOrderId().equals(orderId)
                        && payment.getStatus() == PaymentStatus.UNSUCCESSFUL));
    }
}