package inno.authservice.messaging;

import java.util.UUID;

public record UserStatusEvent(UUID userId, boolean active) {
}
