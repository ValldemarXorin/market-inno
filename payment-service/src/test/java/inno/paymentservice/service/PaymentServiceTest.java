package inno.paymentservice.service;

import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import inno.paymentservice.client.StripePaymentClient;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.dto.response.TotalResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentCurrency;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import inno.paymentservice.event.CreatePaymentEventProducer;
import inno.paymentservice.exception.custom_exception.StripePaymentException;
import inno.paymentservice.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private StripePaymentClient stripePaymentClient;

    @Mock
    private CreatePaymentEventProducer createPaymentEventProducer;

    @InjectMocks
    private PaymentService paymentService;

    private UUID testPaymentId;
    private UUID testOrderId;
    private UUID testUserId;
    private LocalDateTime testTimestamp;
    private Payment testPayment;
    private PaymentResponse testPaymentResponse;
    private CreatePaymentRequest testCreatePaymentRequest;

    @BeforeEach
    public void initData() {
        testPaymentId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testTimestamp = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0);

        testPayment = new Payment();
        testPayment.setId(testPaymentId);
        testPayment.setOrderId(testOrderId);
        testPayment.setUserId(testUserId);
        testPayment.setStatus(PaymentStatus.SUCCESSFUL);
        testPayment.setTimestamp(testTimestamp);
        testPayment.setPaymentAmount(new BigDecimal("100.00"));
        testPayment.setCurrency(PaymentCurrency.USD);
        testPayment.setStripePaymentIntentId("pi_test_123");

        testCreatePaymentRequest = new CreatePaymentRequest(
                testOrderId, testUserId, testTimestamp, new BigDecimal("100.00"), PaymentCurrency.USD);

        testPaymentResponse = new PaymentResponse(
                testPaymentId, testOrderId, testUserId, PaymentStatus.SUCCESSFUL,
                testTimestamp, new BigDecimal("100.00"), PaymentCurrency.USD, "pi_test_123");
    }

    @Test
    public void shouldCreateSuccessfulPaymentWhenStripeSucceeds() throws Exception {
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenReturn(paymentIntent("pi_test_123", "succeeded"));
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(testPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(testPaymentResponse);

        PaymentResponse response = paymentService.createPayment(testCreatePaymentRequest);

        assertEquals(testPaymentResponse, response);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();

        assertSame(testPayment, saved);
        assertEquals(PaymentStatus.SUCCESSFUL, saved.getStatus());
        assertEquals("pi_test_123", saved.getStripePaymentIntentId());
        assertEquals(testOrderId, saved.getOrderId());
        assertEquals(testUserId, saved.getUserId());
        assertEquals(testTimestamp, saved.getTimestamp());
        assertEquals(new BigDecimal("100.00"), saved.getPaymentAmount());
        assertEquals(PaymentCurrency.USD, saved.getCurrency());

        ArgumentCaptor<CreatePaymentEvent> eventCaptor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(createPaymentEventProducer, times(1)).publish(eventCaptor.capture());
        CreatePaymentEvent published = eventCaptor.getValue();
        assertEquals(testPaymentId, published.paymentId());
        assertEquals(testOrderId, published.orderId());
        assertEquals(PaymentStatus.SUCCESSFUL, published.status());
    }

    @Test
    public void shouldCreateUnsuccessfulPaymentWhenStripePaymentFails() throws Exception {
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenReturn(paymentIntent("pi_test_456", "requires_payment_method"));
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(testPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.createPayment(testCreatePaymentRequest);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();

        assertEquals(PaymentStatus.UNSUCCESSFUL, saved.getStatus());
        assertEquals("pi_test_456", saved.getStripePaymentIntentId());

        ArgumentCaptor<CreatePaymentEvent> eventCaptor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(createPaymentEventProducer, times(1)).publish(eventCaptor.capture());
        assertEquals(PaymentStatus.UNSUCCESSFUL, eventCaptor.getValue().status());
    }

    @Test
    public void shouldNotSaveOrPublishWhenStripeApiThrows() throws Exception {
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenThrow(new ApiException("card declined", null, "card_declined", 402, null));

        assertThrows(StripePaymentException.class, () -> paymentService.createPayment(testCreatePaymentRequest));

        verify(paymentRepository, never()).save(any());
        verify(createPaymentEventProducer, never()).publish(any());
    }

    @Test
    public void shouldPassOrderBasedIdempotencyKeyToStripe() throws Exception {
        when(stripePaymentClient.createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), any(String.class)))
                .thenReturn(paymentIntent("pi_test_123", "succeeded"));
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(testPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.createPayment(testCreatePaymentRequest);

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(stripePaymentClient).createAndConfirmPaymentIntent(
                any(BigDecimal.class), any(String.class), any(UUID.class), idempotencyKeyCaptor.capture());
        assertEquals("payment-order-" + testOrderId, idempotencyKeyCaptor.getValue());
    }

    @Test
    public void shouldGetPaymentsByUserId() {
        when(paymentRepository.findByUserId(testUserId)).thenReturn(List.of(testPayment));
        when(paymentMapper.toResponse(testPayment)).thenReturn(testPaymentResponse);

        List<PaymentResponse> result = paymentService.getPaymentsByUserId(testUserId);

        assertEquals(1, result.size());
        assertEquals(testPaymentResponse, result.get(0));
        verify(paymentRepository).findByUserId(testUserId);
    }

    @Test
    public void shouldGetPaymentsByOrderId() {
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(List.of(testPayment));
        when(paymentMapper.toResponse(testPayment)).thenReturn(testPaymentResponse);

        List<PaymentResponse> result = paymentService.getPaymentsByOrderId(testOrderId);

        assertEquals(1, result.size());
        assertEquals(testPaymentResponse, result.get(0));
        verify(paymentRepository).findByOrderId(testOrderId);
    }

    @Test
    public void shouldGetPaymentsByStatus() {
        when(paymentRepository.findByStatus(PaymentStatus.SUCCESSFUL)).thenReturn(List.of(testPayment));
        when(paymentMapper.toResponse(testPayment)).thenReturn(testPaymentResponse);

        List<PaymentResponse> result = paymentService.getPaymentsByStatus(PaymentStatus.SUCCESSFUL);

        assertEquals(1, result.size());
        assertEquals(testPaymentResponse, result.get(0));
        verify(paymentRepository).findByStatus(PaymentStatus.SUCCESSFUL);
    }

    @Test
    public void shouldReturnTotalForUserInDateRange() {
        LocalDateTime start = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(paymentRepository.findTotalByUserIdAndTimestampBetween(testUserId, start, end))
                .thenReturn(List.of(total(200.0)));

        TotalResponse result = paymentService.getTotalForUser(testUserId, start, end);

        assertEquals(new BigDecimal("200.0"), result.total());
        verify(paymentRepository).findTotalByUserIdAndTimestampBetween(testUserId, start, end);
    }

    @Test
    public void shouldReturnZeroTotalForUserWhenNoPaymentsMatch() {
        LocalDateTime start = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(paymentRepository.findTotalByUserIdAndTimestampBetween(testUserId, start, end))
                .thenReturn(List.of());

        TotalResponse result = paymentService.getTotalForUser(testUserId, start, end);

        assertEquals(BigDecimal.ZERO, result.total());
    }

    @Test
    public void shouldReturnTotalForAllUsersInDateRange() {
        LocalDateTime start = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(paymentRepository.findTotalByTimestampBetween(start, end))
                .thenReturn(List.of(total(500.0)));

        TotalResponse result = paymentService.getTotalForAllUsers(start, end);

        assertEquals(new BigDecimal("500.0"), result.total());
        verify(paymentRepository).findTotalByTimestampBetween(start, end);
    }

    @Test
    public void shouldReturnZeroTotalForAllUsersWhenNoPaymentsMatch() {
        LocalDateTime start = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(paymentRepository.findTotalByTimestampBetween(start, end))
                .thenReturn(List.of());

        TotalResponse result = paymentService.getTotalForAllUsers(start, end);

        assertEquals(BigDecimal.ZERO, result.total());
    }

    private PaymentIntent paymentIntent(String id, String status) {
        PaymentIntent paymentIntent = new PaymentIntent();
        paymentIntent.setId(id);
        paymentIntent.setStatus(status);
        return paymentIntent;
    }

    private PaymentRepository.Total total(double value) {
        return new PaymentRepository.Total() {
            @Override
            public Double getTotal() {
                return value;
            }
        };
    }
}