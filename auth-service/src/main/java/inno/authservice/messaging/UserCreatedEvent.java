package inno.authservice.messaging;

import java.util.UUID;

public record UserCreatedEvent(UUID userId) {
}
