package inno.authservice.integration;

import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.entity.OutboxEvent;
import inno.authservice.messaging.UserCreatedEvent;
import inno.authservice.repository.OutboxEventRepository;
import inno.authservice.service.UserCredentialsService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "user-created-events")
class UserCreatedEventPublishingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("authservice")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("auth.jwt.secret",
                () -> "integration-test-secret-at-least-32-bytes-long!!");
    }

    @Autowired
    private UserCredentialsService userCredentialsService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void registrationShouldPersistOutboxEventAndPublishUserCreatedEventWithCredentialsId() {
        RegisterResponse registered = userCredentialsService.register(
                "provision_user", "password123");

        OutboxEvent outboxEvent = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(registered.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("outbox event not found for " + registered.id()));
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEvent.TYPE_USER_CREATED);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(registered.id());
        assertThat(outboxEvent.getPayload()).contains(registered.id().toString());

        List<UserCreatedEvent> events = awaitEvents(registered.id(), 1);

        assertThat(events).hasSize(1);
        UserCreatedEvent event = events.get(0);
        assertThat(event.userId()).isEqualTo(registered.id());
    }

    private List<UserCreatedEvent> awaitEvents(UUID userId, int expectedCount) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());

        List<UserCreatedEvent> events = new ArrayList<>();
        try (KafkaConsumer<String, UserCreatedEvent> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new JsonDeserializer<>(UserCreatedEvent.class))) {
            consumer.subscribe(List.of("user-created-events"));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && events.size() < expectedCount) {
                ConsumerRecords<String, UserCreatedEvent> consumerRecords = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, UserCreatedEvent> consumerRecord : consumerRecords) {
                    if (userId.equals(consumerRecord.value().userId())) {
                        events.add(consumerRecord.value());
                    }
                }
            }
        }
        if (events.size() < expectedCount) {
            fail("expected " + expectedCount + " events, got " + events.size());
        }
        return events;
    }
}
