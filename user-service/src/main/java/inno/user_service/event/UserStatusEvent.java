package inno.user_service.event;

import java.util.UUID;

public record UserStatusEvent(UUID userId, boolean active) {
}
