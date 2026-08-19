package inno.orderservice.exception;

import inno.orderservice.dto.response.ErrorResponse;
import inno.orderservice.exception.custom_exception.ItemNotFoundException;
import inno.orderservice.exception.custom_exception.OrderNotFoundException;
import inno.orderservice.exception.custom_exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");

    @Test
    void shouldReturnNotFoundForBusinessExceptions() {
        ResponseEntity<ErrorResponse> orderNotFound =
                handler.handleOrderNotFound(new OrderNotFoundException(UUID.randomUUID()), request);
        assertEquals(HttpStatus.NOT_FOUND, orderNotFound.getStatusCode());
        assertEquals("/orders", orderNotFound.getBody().path());

        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleItemNotFound(new ItemNotFoundException(UUID.randomUUID()), request).getStatusCode());

        ResponseEntity<ErrorResponse> userNotFound =
                handler.handleUserNotFound(new UserNotFoundException("vova@gmail.com"), request);
        assertEquals(HttpStatus.NOT_FOUND, userNotFound.getStatusCode());
        assertEquals("User not found with email: vova@gmail.com", userNotFound.getBody().message());
    }

    @Test
    void shouldReturnBadRequestWithJoinedFieldErrorsForValidationException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Payload(null, null), "payload");
        bindingResult.rejectValue("email", "email", "Email must not be blank");
        bindingResult.rejectValue("items", "items", "Order must contain at least one item");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("email: Email must not be blank; items: Order must contain at least one item",
                response.getBody().message());
    }

    @Test
    void shouldRethrowAccessDeniedException() {
        assertThrows(AccessDeniedException.class,
                () -> handler.handleAccessDenied(new AccessDeniedException("denied")));
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(new IllegalStateException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected error occurred", response.getBody().message());
        assertEquals("/orders", response.getBody().path());
    }

    private record Payload(String email, java.util.List<String> items) {
    }
}