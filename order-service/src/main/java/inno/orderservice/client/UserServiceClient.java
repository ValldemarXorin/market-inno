package inno.orderservice.client;

import inno.orderservice.dto.response.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient userServiceRestClient;

    @Retry(name = "userService", fallbackMethod = "getUserByEmailFallback")
    @CircuitBreaker(name = "userService")
    public UserResponse getUserByEmail(String email) {
        return userServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users/by-email/{email}").build(email))
                .retrieve()
                .body(UserResponse.class);
    }

    private UserResponse getUserByEmailFallback(String email, Throwable throwable) {
        log.warn("User service call failed, returning fallback response: email={}, cause={}", email,
                throwable.getMessage());
        return null;
    }
}