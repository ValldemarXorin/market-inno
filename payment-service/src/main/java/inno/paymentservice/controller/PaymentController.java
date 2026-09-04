package inno.paymentservice.controller;

import inno.paymentservice.dto.request.CreatePaymentRequest;
import inno.paymentservice.dto.response.PaymentResponse;
import inno.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest createPaymentRequest) {
        PaymentResponse paymentResponseCreated = paymentService.createPayment(createPaymentRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + paymentResponseCreated.id()))
                .body(paymentResponseCreated);
    }
}