package inno.user_service.event;

import inno.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedEventConsumer.class);

    private final UserService userService;

    @KafkaListener(topics = "${app.kafka.topic.user-created:user-created-events}")
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Received user created event: userId={}", event.userId());
        userService.provisionUser(event);
    }
}
