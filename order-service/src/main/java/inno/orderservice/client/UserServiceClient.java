package inno.orderservice.client;

import inno.orderservice.dto.response.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByUserIdFallback")
    public UserResponse getUserByUserId(UUID userId) {
        return userServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users/{userId}").build(userId))
                .retrieve()
                .body(UserResponse.class);
    }

    private UserResponse getUserByUserIdFallback(UUID userId, Throwable throwable) {
        return null;
    }
}