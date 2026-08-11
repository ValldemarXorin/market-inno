package inno.user_service;

import com.redis.testcontainers.RedisContainer;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.entity.User;
import inno.user_service.event.UserCreatedEvent;
import inno.user_service.event.UserStatusEvent;
import inno.user_service.service.UserService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"user-created-events", "user-status-events"})
class UserProvisioningIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("userservice")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Value("${app.kafka.topic.user-created:user-created-events}")
    private String userCreatedTopic;

    @Value("${app.kafka.topic.user-status:user-status-events}")
    private String userStatusTopic;

    @Test
    void shouldProvisionUserWithSharedUuidRejectDuplicatesAndSyncDeactivation() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent provisioning = new UserCreatedEvent(userId);

        kafkaTemplate.send(userCreatedTopic, userId.toString(), provisioning).get();

        await(() -> userRepository.existsById(userId));
        User user = userRepository.findById(userId).orElseThrow();
        assertEquals(userId, user.getId());
        assertNull(user.getEmail());

        kafkaTemplate.send(userCreatedTopic, userId.toString(), provisioning).get();
        List<UserCreatedEvent> provisioningEvents =
                awaitEvents("user-created-events", userId, UserCreatedEvent.class, UserCreatedEvent::userId, 2);
        assertEquals(2, provisioningEvents.size());
        Thread.sleep(1000);
        assertEquals(1, userRepository.count());

        userService.setUserActive(userId, false);
        await(() -> !userRepository.findById(userId).orElseThrow().getActive());
        List<UserStatusEvent> deactivatedEvents =
                awaitEvents(userStatusTopic, userId, UserStatusEvent.class, UserStatusEvent::userId, 1);
        assertEquals(userId, deactivatedEvents.get(0).userId());
        assertFalse(deactivatedEvents.get(0).active());

        userService.setUserActive(userId, true);
        await(() -> userRepository.findById(userId).orElseThrow().getActive());
        List<UserStatusEvent> activatedEvents =
                awaitEvents(userStatusTopic, userId, UserStatusEvent.class, UserStatusEvent::userId, 2);
        assertEquals(userId, activatedEvents.get(1).userId());
        assertTrue(activatedEvents.get(1).active());
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

    private <T> List<T> awaitEvents(String topic, UUID userId, Class<T> type,
                                    Function<T, UUID> idExtractor, int expectedCount) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());

        List<T> events = new ArrayList<>();
        try (KafkaConsumer<String, T> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new JsonDeserializer<>(type))) {
            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && events.size() < expectedCount) {
                ConsumerRecords<String, T> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, T> record : records) {
                    if (userId.equals(idExtractor.apply(record.value()))) {
                        events.add(record.value());
                    }
                }
            }
        }
        if (events.size() < expectedCount) {
            fail("expected " + expectedCount + " events on " + topic + ", got " + events.size());
        }
        return events;
    }
}
