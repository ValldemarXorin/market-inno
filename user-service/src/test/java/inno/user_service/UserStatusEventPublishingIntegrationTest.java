package inno.user_service;

import com.redis.testcontainers.RedisContainer;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.entity.User;
import inno.user_service.event.UserStatusEvent;
import inno.user_service.service.UserService;
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
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "user-status-events")
class UserStatusEventPublishingIntegrationTest {

    private static final int EXPECTED_EVENTS = 2;

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
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void shouldPublishDeactivatedAndActivatedEventsToKafka() {
        User user = new User();
        user.setUsername("Masha");
        user.setSurname("Mashina");
        user.setEmail("masha-events-" + UUID.randomUUID() + "@yandex.ru");
        User saved = userRepository.save(user);

        userService.setUserActive(saved.getId(), false);
        userService.setUserActive(saved.getId(), true);

        List<UserStatusEvent> events = awaitEvents(saved.getId(), EXPECTED_EVENTS);

        assertEquals(EXPECTED_EVENTS, events.size());
        assertFalse(events.get(0).active());
        assertTrue(events.get(1).active());
    }

    private List<UserStatusEvent> awaitEvents(UUID userId, int expectedCount) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());

        List<UserStatusEvent> events = new ArrayList<>();
        try (KafkaConsumer<String, UserStatusEvent> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new JsonDeserializer<>(UserStatusEvent.class))) {
            consumer.subscribe(List.of("user-status-events"));

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && events.size() < expectedCount) {
                ConsumerRecords<String, UserStatusEvent> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, UserStatusEvent> record : records) {
                    if (userId.equals(record.value().userId())) {
                        events.add(record.value());
                    }
                }
            }
        }
        return events;
    }
}
