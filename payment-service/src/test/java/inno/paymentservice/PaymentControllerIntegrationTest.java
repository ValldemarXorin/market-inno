package inno.paymentservice;

import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import inno.paymentservice.client.StripePaymentClient;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentCurrency;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "stripe.secret-key=sk_test_placeholder")
@EmbeddedKafka(partitions = 1, topics = {"create-payment-events"})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    private static final String TOPIC = "create-payment-events";

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> mongo.getConnectionString() + "/payments?directConnection=true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @MockBean
    private StripePaymentClient stripePaymentClient;

    @TestConfiguration
    static class KafkaTestConfig {
        @Bean
        public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties,
                                                               EmbeddedKafkaBroker embeddedKafkaBroker) {
            Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
            return new DefaultKafkaProducerFactory<>(props,
                    new StringSerializer(),
                    new JsonSerializer<>(JacksonUtils.enhancedObjectMapper()));
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void shouldCreateSuccessfulPaymentPersistingToMongoAndProducingKafkaEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenReturn(paymentIntent("pi_test_123", "succeeded"));

        var result = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "2026-01-01T12:00:00", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$.paymentAmount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.stripePaymentIntentId").value("pi_test_123"))
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
        assertEquals(PaymentCurrency.USD, persisted.getCurrency());
        assertEquals("pi_test_123", persisted.getStripePaymentIntentId());
        assertEquals(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0), persisted.getTimestamp());

        List<CreatePaymentEvent> events = awaitEvents(TOPIC, created.id(), CreatePaymentEvent.class, 1);
        CreatePaymentEvent event = events.get(0);
        assertEquals(created.id(), event.paymentId());
        assertEquals(orderId, event.orderId());
        assertEquals(PaymentStatus.SUCCESSFUL, event.status());
    }

    @Test
    void shouldCreateUnsuccessfulPaymentWhenStripePaymentFails() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenReturn(paymentIntent("pi_test_456", "requires_payment_method"));

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
        assertEquals("pi_test_456", persisted.getStripePaymentIntentId());

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
                                    {"orderId":null,"userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":-5,"currency":"USD"}
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

    @Test
    void shouldReturnBadGatewayWhenStripeApiThrows() throws Exception {
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenThrow(new ApiException("card declined", null, "card_declined", 402, null));

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "2026-01-01T12:00:00", "100.00")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString(
                                "Stripe payment failed for order " + orderId)));

        assertEquals(0, paymentRepository.count());
    }

    private PaymentIntent paymentIntent(String id, String status) {
        PaymentIntent paymentIntent = new PaymentIntent();
        paymentIntent.setId(id);
        paymentIntent.setStatus(status);
        return paymentIntent;
    }

    private String paymentJson(UUID orderId, UUID userId, String timestamp, String amount) {
        return """
                {"orderId":"%s","userId":"%s","timestamp":"%s","paymentAmount":%s,"currency":"USD"}
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