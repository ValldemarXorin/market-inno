package inno.authservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import inno.authservice.entity.OutboxEvent;
import inno.authservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    private static final String TOPIC = "user-created-events";

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxEventPublisher publisher;

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(
                outboxEventRepository,
                kafkaTemplate,
                new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "userCreatedTopic", TOPIC);

        event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(OutboxEvent.TYPE_USER_CREATED);
        event.setAggregateId(UUID.randomUUID());
        event.setPayload("{\"userId\":\"" + event.getAggregateId() + "\"}");
    }

    @Test
    void publishPendingEvents_shouldSendEventToKafkaAndMarkPublished() {
        when(outboxEventRepository.findTop10ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        ArgumentCaptor<UserCreatedEvent> captor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(event.getAggregateId().toString()), captor.capture());

        assertThat(captor.getValue().userId()).isEqualTo(event.getAggregateId());

        verify(outboxEventRepository).markPublished(event.getId());
        verify(outboxEventRepository, never()).incrementAttempts(any());
    }

    @Test
    void publishPendingEvents_shouldNotMarkPublished_whenKafkaPublicationFails() {
        when(outboxEventRepository.findTop10ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        publisher.publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(event.getId());
        verify(outboxEventRepository).incrementAttempts(event.getId());
    }

    @Test
    void publishPendingEvents_shouldRetryUntilEventuallyPublished() {
        when(outboxEventRepository.findTop10ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();
        publisher.publishPendingEvents();

        verify(outboxEventRepository, times(1)).incrementAttempts(event.getId());
        verify(outboxEventRepository).markPublished(event.getId());
    }
}
