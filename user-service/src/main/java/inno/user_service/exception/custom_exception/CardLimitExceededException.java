package inno.user_service.exception.custom_exception;

import java.util.UUID;

public class CardLimitExceededException extends RuntimeException {
    public CardLimitExceededException(UUID userId) {
        super("User " + userId + " already has the maximum allowed number of cards (5)");
    }
}
