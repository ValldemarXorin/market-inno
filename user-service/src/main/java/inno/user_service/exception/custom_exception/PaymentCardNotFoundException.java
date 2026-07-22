package inno.user_service.exception.custom_exception;

import java.util.UUID;

public class PaymentCardNotFoundException extends RuntimeException {
    public PaymentCardNotFoundException(UUID id) {
        super("Payment card not found with id: " + id);
    }
}
