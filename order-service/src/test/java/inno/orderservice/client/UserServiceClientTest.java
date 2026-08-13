package inno.orderservice.client;

import inno.orderservice.dto.response.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock
    private RestClient restClient;

    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        userServiceClient = new UserServiceClient(restClient);
    }

    @Test
    void shouldFetchUserByUserId() {
        UUID userId = UUID.randomUUID();
        UserResponse expected = new UserResponse(
                userId, "vova", "khorin", null, "vova@gmail.com", true, null, null);

        RestClient.RequestHeadersUriSpec<?> requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(any(Function.class));
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(UserResponse.class)).thenReturn(expected);

        UserResponse actual = userServiceClient.getUserByUserId(userId);

        assertSame(expected, actual);
        verify(restClient).get();
        verify(requestSpec).retrieve();
        verify(responseSpec).body(UserResponse.class);

        ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestSpec).uri(uriCaptor.capture());
        URI uri = uriCaptor.getValue().apply(UriComponentsBuilder.newInstance());
        assertEquals("/users/" + userId, uri.getPath());
    }

    @Test
    void shouldBeProtectedByCircuitBreaker() throws Exception {
        Method method = UserServiceClient.class.getMethod("getUserByUserId", UUID.class);

        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);

        assertNotNull(annotation);
        assertEquals("userService", annotation.name());
        assertEquals("getUserByUserIdFallback", annotation.fallbackMethod());
    }
}