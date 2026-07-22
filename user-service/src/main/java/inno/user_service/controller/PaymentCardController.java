package inno.user_service.controller;

import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentCardController {
    private final PaymentCardService paymentCardService;

    @PostMapping("/api/v1/users/{userId}/cards")
    public ResponseEntity<PaymentCardResponse> createPaymentCard(
            @PathVariable UUID userId,
            @Valid @RequestBody CreatePaymentCardRequest createPaymentCardRequest) {
        PaymentCardResponse paymentCardResponseCreated = paymentCardService.createPaymentCard(userId, createPaymentCardRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/cards/" + paymentCardResponseCreated.id()))
                .body(paymentCardResponseCreated);
    }

    @GetMapping("/api/v1/users/{userId}/cards")
    public ResponseEntity<Page<PaymentCardResponse>> getPaymentCardsByUserId(
            @PathVariable UUID userId,
            Pageable pageable) {
        return ResponseEntity.ok(paymentCardService.getPaymentCardsByUserId(userId, pageable));
    }

    @GetMapping("/api/v1/cards/{id}")
    public ResponseEntity<PaymentCardResponse> getPaymentCardById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentCardService.getPaymentCardById(id));
    }

    @GetMapping("/api/v1/cards")
    public ResponseEntity<Page<PaymentCardResponse>> getAllPaymentCards(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String surname,
            Pageable pageable) {
        return ResponseEntity.ok(paymentCardService.getAllPaymentCards(username, surname, pageable));
    }

    @PutMapping("api/v1/cards/{id}")
    public ResponseEntity<PaymentCardResponse> updatePaymentCard(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentCardRequest updatePaymentCardRequest) {
        return ResponseEntity.ok(paymentCardService.updatePaymentCard(id, updatePaymentCardRequest));
    }

    @PatchMapping("/api/v1/cards/{id}/active")
    public ResponseEntity<Void> setPaymentCardActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest setActiveRequest) {
        paymentCardService.setPaymentCardActive(id, setActiveRequest.active());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/cards/{id}")
    public ResponseEntity<Void> deletePaymentCard(@PathVariable UUID id) {
        paymentCardService.deletePaymentCard(id);
        return ResponseEntity.noContent().build();
    }
}
