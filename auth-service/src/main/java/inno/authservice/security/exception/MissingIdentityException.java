package inno.authservice.security.exception;

public class MissingIdentityException extends RuntimeException {

    public MissingIdentityException(String message) {
        super(message);
    }
}