package inno.user_service.event;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserStatusEventPublisherTest {

    private static final String TOPIC = "user-status-events";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserStatusEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "userStatusTopic", TOPIC);
    }

    @Test
    public void shouldSendUserStatusEventWhenUserDeactivated() {
        UUID userId = UUID.randomUUID();

        publisher.onUserDeactivated(new UserDeactivatedEvent(userId));

        ArgumentCaptor<UserStatusEvent> captor = ArgumentCaptor.forClass(UserStatusEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), captor.capture());
        assertEquals(userId, captor.getValue().userId());
        assertFalse(captor.getValue().active());
    }

    @Test
    public void shouldSendUserStatusEventWhenUserActivated() {
        UUID userId = UUID.randomUUID();

        publisher.onUserActivated(new UserActivatedEvent(userId));

        ArgumentCaptor<UserStatusEvent> captor = ArgumentCaptor.forClass(UserStatusEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), captor.capture());
        assertEquals(userId, captor.getValue().userId());
        assertTrue(captor.getValue().active());
    }
}
