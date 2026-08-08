package inno.user_service.event;

import inno.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCreatedEventConsumerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserCreatedEventConsumer consumer;

    @Test
    public void shouldDelegateProvisioningToUserService() {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent event = new UserCreatedEvent(
                userId, "Masha", "Mashina",
                LocalDate.of(1998, Month.OCTOBER, 12), "masha@yandex.ru");

        consumer.onUserCreated(event);

        verify(userService).provisionUser(event);
    }
}
