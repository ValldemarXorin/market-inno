package inno.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"create-payment-events"})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    private static final String TOPIC = "create-payment-events";

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

    @Autowired
    private EmbeddedKafkaBroker broker;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        stubRandomNumber(4);
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void shouldCreateSuccessfulPaymentPersistingToPostgresAndProducingKafkaEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var result = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "2026-01-01T12:00:00", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$.paymentAmount").value(100.00))
                .andReturn();

        PaymentResponse created =
                objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        assertNotNull(created.id());
        assertEquals("/api/v1/payments/" + created.id(), result.getResponse().getHeader("Location"));
        assertEquals(PaymentStatus.SUCCESSFUL, created.status());

        Payment persisted = paymentRepository.findById(created.id()).orElseThrow();
        assertEquals(created.id(), persisted.getId());
        assertEquals(orderId, persisted.getOrderId());
        assertEquals(userId, persisted.getUserId());
        assertEquals(PaymentStatus.SUCCESSFUL, persisted.getStatus());
        assertEquals(new BigDecimal("100.00"), persisted.getPaymentAmount());
        assertEquals(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0), persisted.getTimestamp());

        List<CreatePaymentEvent> events = awaitEvents(TOPIC, created.id(), CreatePaymentEvent.class, 1);
        CreatePaymentEvent event = events.get(0);
        assertEquals(created.id(), event.paymentId());
        assertEquals(orderId, event.orderId());
        assertEquals(PaymentStatus.SUCCESSFUL, event.status());
    }

    @Test
    void shouldCreateUnsuccessfulPaymentWhenRandomNumberIsOdd() throws Exception {
        stubRandomNumber(5);

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String responseContent = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "2026-01-01T12:00:00", "50.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNSUCCESSFUL"))
                .andReturn().getResponse().getContentAsString();

        PaymentResponse created = objectMapper.readValue(responseContent, PaymentResponse.class);
        assertNotNull(created.id());

        Payment persisted = paymentRepository.findById(created.id()).orElseThrow();
        assertEquals(PaymentStatus.UNSUCCESSFUL, persisted.getStatus());
        assertEquals(new BigDecimal("50.00"), persisted.getPaymentAmount());

        List<CreatePaymentEvent> events = awaitEvents(TOPIC, created.id(), CreatePaymentEvent.class, 1);
        CreatePaymentEvent event = events.get(0);
        assertEquals(created.id(), event.paymentId());
        assertEquals(orderId, event.orderId());
        assertEquals(PaymentStatus.UNSUCCESSFUL, event.status());
    }

    @Test
    void shouldReturnBadRequestAndPersistNothingOnInvalidRequest() throws Exception {
        KafkaConsumer<String, String> consumer = newKafkaConsumer();
        try {
            consumer.subscribe(List.of(TOPIC));
            awaitAssignment(consumer);
            consumer.seekToEnd(consumer.assignment());

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"orderId":null,"userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":-5}
                                    """.formatted(UUID.randomUUID())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").exists());

            assertEquals(0, paymentRepository.count());

            assertNoRecordsAfterSeek(consumer, Duration.ofSeconds(2));
        } finally {
            consumer.close();
        }
    }

    private void stubRandomNumber(int number) {
        wireMockServer.stubFor(get(urlPathMatching("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"number\":" + number + "}")));
    }

    private String paymentJson(UUID orderId, UUID userId, String timestamp, String amount) {
        return """
                {"orderId":"%s","userId":"%s","timestamp":"%s","paymentAmount":%s}
                """.formatted(orderId, userId, timestamp, amount);
    }

    private <T> List<T> awaitEvents(String topicName, UUID paymentId, Class<T> type,
                                    int expectedCount) {
        Map<String, Object> props = consumerProps();
        List<T> events = new ArrayList<>();
        try (KafkaConsumer<String, T> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new JsonDeserializer<>(type))) {
            consumer.subscribe(List.of(topicName));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && events.size() < expectedCount) {
                ConsumerRecords<String, T> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, T> record : records) {
                    if (paymentId.toString().equals(record.key())) {
                        events.add(record.value());
                    }
                }
            }
        }
        if (events.size() < expectedCount) {
            fail("expected " + expectedCount + " events on " + topicName + " for paymentId " + paymentId
                    + ", got " + events.size());
        }
        return events;
    }

    private KafkaConsumer<String, String> newKafkaConsumer() {
        return new KafkaConsumer<>(consumerProps(), new StringDeserializer(), new StringDeserializer());
    }

    private void awaitAssignment(KafkaConsumer<String, String> consumer) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(100));
        }
        if (consumer.assignment().isEmpty()) {
            fail("consumer did not get a partition assignment within timeout");
        }
    }

    private void assertNoRecordsAfterSeek(KafkaConsumer<String, String> consumer, Duration wait) {
        long deadline = System.currentTimeMillis() + wait.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            if (!records.isEmpty()) {
                fail("expected no events but received " + records.count());
            }
        }
    }

    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }
}