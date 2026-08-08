package inno.authservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCreatedEventPublisherTest {

    private static final String TOPIC = "user-created-events";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserCreatedEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "userCreatedTopic", TOPIC);
    }

    @Test
    void onUserCreated_shouldSendEventToKafka() {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent event = new UserCreatedEvent(userId);

        publisher.onUserCreated(event);

        ArgumentCaptor<UserCreatedEvent> captor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), captor.capture());

        assertThat(captor.getValue().userId()).isEqualTo(userId);
    }
}
