package inno.authservice.messaging;

import inno.authservice.exception.custom_exception.UserNotFoundException;
import inno.authservice.service.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserStatusConsumerTest {

    @Mock private UserCredentialsService userCredentialsService;

    @InjectMocks
    private UserStatusConsumer userStatusConsumer;

    @Test
    void onEvent_shouldDeactivate_whenInactive() {
        UUID userId = UUID.randomUUID();

        userStatusConsumer.onUserStatusEvent(new UserStatusEvent(userId, false));

        verify(userCredentialsService).deactivate(userId);
        verify(userCredentialsService, never()).activate(any());
    }

    @Test
    void onEvent_shouldActivate_whenActive() {
        UUID userId = UUID.randomUUID();

        userStatusConsumer.onUserStatusEvent(new UserStatusEvent(userId, true));

        verify(userCredentialsService).activate(userId);
        verify(userCredentialsService, never()).deactivate(any());
    }

    @Test
    void onEvent_shouldSkip_whenCredentialsNotFound() {
        UUID userId = UUID.randomUUID();
        doThrow(new UserNotFoundException("User not found: " + userId))
                .when(userCredentialsService).deactivate(userId);

        assertThatCode(() -> userStatusConsumer.onUserStatusEvent(new UserStatusEvent(userId, false)))
                .doesNotThrowAnyException();

        verify(userCredentialsService).deactivate(userId);
    }
}
