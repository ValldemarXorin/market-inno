package inno.user_service.security.exception;

public class MissingIdentityException extends RuntimeException {
    public MissingIdentityException(String message) { super(message); }
}
