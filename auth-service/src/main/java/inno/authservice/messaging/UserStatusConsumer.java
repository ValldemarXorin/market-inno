package inno.authservice.messaging;

import inno.authservice.exception.custom_exception.UserNotFoundException;
import inno.authservice.service.UserCredentialsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserStatusConsumer.class);

    private final UserCredentialsService userCredentialsService;

    @KafkaListener(topics = "${app.kafka.topic.user-status:user-status-events}")
    public void onUserStatusEvent(UserStatusEvent event) {
        log.info("Received user status event: userId={}, active={}", event.userId(), event.active());

        if (event.active()) {
            userCredentialsService.activate(event.userId());
            return;
        }

        try {
            userCredentialsService.deactivate(event.userId());
        } catch (UserNotFoundException e) {
            log.warn("Credentials not found for user {}, skipping event", event.userId());
        }
    }
}
