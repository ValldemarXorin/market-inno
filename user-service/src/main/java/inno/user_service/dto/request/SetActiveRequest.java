package inno.user_service.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetActiveRequest(
        @NotNull(message = "Active status must not be null")
        Boolean active
) {
}