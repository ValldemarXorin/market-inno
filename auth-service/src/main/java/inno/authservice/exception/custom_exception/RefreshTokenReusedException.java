package inno.authservice.exception.custom_exception;

public class RefreshTokenReusedException extends RuntimeException {
    public RefreshTokenReusedException(String message) {
        super(message);
    }
}