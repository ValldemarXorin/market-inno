package inno.orderservice;

import inno.orderservice.security.CurrentUser;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

public final class TestIdentity {

    private TestIdentity() {
    }

    public static HttpHeaders adminHeaders() {
        return identityHeaders(UUID.randomUUID(), "ADMIN");
    }

    public static HttpHeaders userHeaders(UUID userId) {
        return identityHeaders(userId, "USER");
    }

    public static HttpHeaders identityHeaders(UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CurrentUser.USER_ID_HEADER, userId.toString());
        headers.set(CurrentUser.USER_ROLE_HEADER, role);
        return headers;
    }
}