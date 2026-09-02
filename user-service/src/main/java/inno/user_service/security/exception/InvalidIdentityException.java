package inno.user_service.security.exception;

public class InvalidIdentityException extends RuntimeException {
    public InvalidIdentityException(String message) { super(message); }
}
