package inno.authservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import inno.authservice.entity.OutboxEvent;
import inno.authservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.user-created:user-created-events}")
    private String userCreatedTopic;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop10ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            UserCreatedEvent payload = objectMapper.readValue(event.getPayload(), UserCreatedEvent.class);
            kafkaTemplate.send(userCreatedTopic, event.getAggregateId().toString(), payload)
                    .get(5, TimeUnit.SECONDS);
            outboxEventRepository.markPublished(event.getId());
            log.info("Published outbox event: id={}, type={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            outboxEventRepository.incrementAttempts(event.getId());
            log.error("Failed to publish outbox event: id={}, type={}, aggregateId={}, attempts={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), event.getAttempts() + 1, e);
        }
    }
}
