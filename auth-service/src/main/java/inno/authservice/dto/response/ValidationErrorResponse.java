package inno.authservice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        List<FieldValidationError> fieldErrors,
        String path
) {
    public record FieldValidationError(String field, String message) {}
}
