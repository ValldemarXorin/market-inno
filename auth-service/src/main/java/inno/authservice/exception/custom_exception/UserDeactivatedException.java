package inno.authservice.exception.custom_exception;

public class UserDeactivatedException extends RuntimeException {
    public UserDeactivatedException(String message) {
        super(message);
    }
}
