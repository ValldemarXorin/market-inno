package inno.paymentservice.service;

import inno.paymentservice.client.RandomNumberClient;
import inno.paymentservice.dao.repository.PaymentRepository;
import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.dto.response.TotalResponse;
import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import inno.paymentservice.event.CreatePaymentEvent;
import inno.paymentservice.event.CreatePaymentEventProducer;
import inno.paymentservice.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final CreatePaymentEventProducer createPaymentEventProducer;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(determineStatus(randomNumberClient.getRandomNumber().number()));

        Payment saved = paymentRepository.save(payment);
        createPaymentEventProducer.publish(
                new CreatePaymentEvent(saved.getId(), saved.getOrderId(), saved.getStatus()));
        return paymentMapper.toResponse(saved);
    }

    public List<PaymentResponse> getPaymentsByUserId(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    public TotalResponse getTotalForUser(UUID userId, LocalDateTime start, LocalDateTime end) {
        return new TotalResponse(
                paymentRepository.sumPaymentAmountByUserIdAndTimestampBetween(userId, start, end));
    }

    public TotalResponse getTotalForAllUsers(LocalDateTime start, LocalDateTime end) {
        return new TotalResponse(
                paymentRepository.sumPaymentAmountByTimestampBetween(start, end));
    }

    private PaymentStatus determineStatus(int number) {
        return number % 2 == 0 ? PaymentStatus.SUCCESSFUL : PaymentStatus.UNSUCCESSFUL;
    }
}