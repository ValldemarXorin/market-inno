package inno.user_service.event;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserStatusEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserStatusEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.user-status:user-status-events}")
    private String userStatusTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeactivated(UserDeactivatedEvent event) {
        publish(event.userId(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserActivated(UserActivatedEvent event) {
        publish(event.userId(), true);
    }

    private void publish(UUID userId, boolean active) {
        log.info("Publishing user status event: userId={}, active={}", userId, active);
        kafkaTemplate.send(userStatusTopic, userId.toString(), new UserStatusEvent(userId, active));
    }
}
