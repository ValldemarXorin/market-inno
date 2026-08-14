package inno.orderservice.event;

import inno.orderservice.TestTokens;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.entity.Order;
import inno.orderservice.entity.OrderStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payment-created-events"})
class PaymentCreatedEventKafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("orderservice")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Value("${app.kafka.topic.payment-created:payment-created-events}")
    private String topic;

    private UUID orderId;

    @BeforeEach
    void createOrder() {
        orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(UUID.randomUUID());
        order.setUserEmail("vova@gmail.com");
        order.setStatus(OrderStatus.CREATED);
        order.setTotalPrice(new BigDecimal("30.00"));
        orderRepository.save(order);
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    @Test
    void shouldProcessSuccessfulPaymentAndIgnoreDuplicate() throws Exception {
        UUID paymentId = UUID.randomUUID();
        await(() -> orderRepository.findById(orderId).isPresent());
        Order before = orderRepository.findById(orderId).orElseThrow();
        LocalDateTime createdAt = before.getCreatedAt();

        kafkaTemplate.send(topic, paymentId.toString(),
                new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL)).join();

        await(() -> orderRepository.findById(orderId).orElseThrow().getStatus() == OrderStatus.COMPLETED);
        Order processed = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, processed.getStatus());
        assertEquals(createdAt, processed.getCreatedAt());
        LocalDateTime updatedAfterFirst = processed.getUpdatedAt();

        kafkaTemplate.send(topic, paymentId.toString(),
                new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL)).join();
        Thread.sleep(1500);

        Order afterDuplicate = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, afterDuplicate.getStatus());
        assertEquals(updatedAfterFirst, afterDuplicate.getUpdatedAt());
    }

    @Test
    void shouldUsePaymentIdAsKafkaKey() throws Exception {
        await(() -> orderRepository.findById(orderId).isPresent());

        UUID paymentId = UUID.randomUUID();
        kafkaTemplate.send(topic, paymentId.toString(),
                new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL)).join();

        awaitEvents(topic, paymentId, PaymentCreatedEvent.class, 1);
    }

    @Test
    void shouldCancelOrderOnUnsuccessfulPayment() throws Exception {
        UUID paymentId = UUID.randomUUID();
        await(() -> orderRepository.findById(orderId).isPresent());

        kafkaTemplate.send(topic, paymentId.toString(),
                new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.UNSUCCESSFUL)).join();

        await(() -> orderRepository.findById(orderId).orElseThrow().getStatus() == OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void shouldRejectEventWhenKafkaKeyDoesNotMatchPaymentId() throws Exception {
        await(() -> orderRepository.findById(orderId).isPresent());

        UUID paymentId = UUID.randomUUID();
        kafkaTemplate.send(topic, UUID.randomUUID().toString(),
                new PaymentCreatedEvent(paymentId, orderId, PaymentStatus.SUCCESSFUL)).join();

        Thread.sleep(1500);
        assertEquals(OrderStatus.CREATED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    private void await(Supplier<Boolean> condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("condition not met within timeout");
    }

    private <T> List<T> awaitEvents(String topicName, UUID paymentId, Class<T> type,
                                    int expectedCount) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());

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
            fail("expected " + expectedCount + " events on " + topicName + ", got " + events.size());
        }
        return events;
    }
}