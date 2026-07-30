package inno.user_service.exception.custom_exception;

public class CardNumberAlreadyExistsException extends RuntimeException {
    public CardNumberAlreadyExistsException(String number) {
        super("Payment card with number " + number + " already exists");
    }
}
