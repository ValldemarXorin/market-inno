package inno.paymentservice.service;

import inno.paymentservice.client.RandomNumberClient;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.dto.response.RandomResponse;
import inno.paymentservice.dto.response.TotalResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import inno.paymentservice.event.CreatePaymentEventProducer;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RandomNumberClient randomNumberClient;

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

        testCreatePaymentRequest = new CreatePaymentRequest(
                testOrderId, testUserId, testTimestamp, new BigDecimal("100.00"));

        testPaymentResponse = new PaymentResponse(
                testPaymentId, testOrderId, testUserId, PaymentStatus.SUCCESSFUL,
                testTimestamp, new BigDecimal("100.00"));
    }

    @Test
    public void shouldCreatePaymentSuccessfullyWhenRandomNumberIsEven() {
        when(randomNumberClient.getRandomNumber()).thenReturn(new RandomResponse(4));
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
        assertEquals(testOrderId, saved.getOrderId());
        assertEquals(testUserId, saved.getUserId());
        assertEquals(testTimestamp, saved.getTimestamp());
        assertEquals(new BigDecimal("100.00"), saved.getPaymentAmount());

        ArgumentCaptor<CreatePaymentEvent> eventCaptor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(createPaymentEventProducer).publish(eventCaptor.capture());
        CreatePaymentEvent published = eventCaptor.getValue();
        assertEquals(testPaymentId, published.paymentId());
        assertEquals(testOrderId, published.orderId());
        assertEquals(PaymentStatus.SUCCESSFUL, published.status());
    }

    @Test
    public void shouldCreatePaymentWithUnsuccessfulStatusWhenRandomNumberIsOdd() {
        when(randomNumberClient.getRandomNumber()).thenReturn(new RandomResponse(5));
        when(paymentMapper.toEntity(any(CreatePaymentRequest.class))).thenReturn(testPayment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.createPayment(testCreatePaymentRequest);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();

        assertEquals(PaymentStatus.UNSUCCESSFUL, saved.getStatus());

        ArgumentCaptor<CreatePaymentEvent> eventCaptor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(createPaymentEventProducer).publish(eventCaptor.capture());
        assertEquals(PaymentStatus.UNSUCCESSFUL, eventCaptor.getValue().status());
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

        when(paymentRepository.sumPaymentAmountByUserIdAndTimestampBetween(testUserId, start, end))
                .thenReturn(new BigDecimal("200.00"));

        TotalResponse result = paymentService.getTotalForUser(testUserId, start, end);

        assertEquals(new BigDecimal("200.00"), result.total());
        verify(paymentRepository).sumPaymentAmountByUserIdAndTimestampBetween(testUserId, start, end);
    }

    @Test
    public void shouldReturnTotalForAllUsersInDateRange() {
        LocalDateTime start = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JANUARY, 31, 23, 59);

        when(paymentRepository.sumPaymentAmountByTimestampBetween(start, end))
                .thenReturn(new BigDecimal("500.00"));

        TotalResponse result = paymentService.getTotalForAllUsers(start, end);

        assertEquals(new BigDecimal("500.00"), result.total());
        verify(paymentRepository).sumPaymentAmountByTimestampBetween(start, end);
    }
}