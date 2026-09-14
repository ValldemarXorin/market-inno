package inno.paymentservice.controller;

import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.entity.PaymentCurrency;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void shouldCreatePaymentAndReturnCreated() throws Exception {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0);
        PaymentResponse paymentResponse = new PaymentResponse(
                paymentId, orderId, userId, PaymentStatus.SUCCESSFUL, timestamp,
                new BigDecimal("100.00"), PaymentCurrency.USD, "pi_test_123");

        when(paymentService.createPayment(any(CreatePaymentRequest.class))).thenReturn(paymentResponse);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":100.00,"currency":"USD"}
                                """.formatted(orderId, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T12:00:00"))
                .andExpect(jsonPath("$.paymentAmount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.stripePaymentIntentId").value("pi_test_123"));

        verify(paymentService).createPayment(any(CreatePaymentRequest.class));
    }

    @Test
    void shouldReturnBadRequestForInvalidBodyAndNotCallService() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":null,"userId":"%s","timestamp":"2026-01-01T12:00:00","paymentAmount":-5,"currency":"USD"}
                                """.formatted(userId)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).createPayment(any());
    }

    @Test
    void shouldReturnBadRequestForMissingBody() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).createPayment(any());
    }
}