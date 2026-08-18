package inno.user_service.controller;

import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.security.util.ResourceSecurityService;
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
    private final ResourceSecurityService resourceSecurity;

    @PostMapping("/users/{userId}/cards")
    public ResponseEntity<PaymentCardResponse> createPaymentCard(
            @PathVariable UUID userId,
            @Valid @RequestBody CreatePaymentCardRequest createPaymentCardRequest) {
        resourceSecurity.requireAdminOrSelf(userId);
        PaymentCardResponse paymentCardResponseCreated = paymentCardService.createPaymentCard(userId, createPaymentCardRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/cards/" + paymentCardResponseCreated.id()))
                .body(paymentCardResponseCreated);
    }

    @GetMapping("/users/{userId}/cards")
    public ResponseEntity<Page<PaymentCardResponse>> getPaymentCardsByUserId(
            @PathVariable UUID userId,
            Pageable pageable) {
        resourceSecurity.requireAdminOrSelf(userId);
        return ResponseEntity.ok(paymentCardService.getPaymentCardsByUserId(userId, pageable));
    }

    @GetMapping("/cards/{id}")
    public ResponseEntity<PaymentCardResponse> getPaymentCardById(@PathVariable UUID id) {
        resourceSecurity.requireAdminOrCardOwner(id);
        return ResponseEntity.ok(paymentCardService.getPaymentCardById(id));
    }

    @GetMapping("/cards")
    public ResponseEntity<Page<PaymentCardResponse>> getAllPaymentCards(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String surname,
            Pageable pageable) {
        resourceSecurity.requireAdmin();
        return ResponseEntity.ok(paymentCardService.getAllPaymentCards(username, surname, pageable));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<PaymentCardResponse> updatePaymentCard(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentCardRequest updatePaymentCardRequest) {
        resourceSecurity.requireAdminOrCardOwner(id);
        return ResponseEntity.ok(paymentCardService.updatePaymentCard(id, updatePaymentCardRequest));
    }

    @PatchMapping("/cards/{id}/active")
    public ResponseEntity<Void> setPaymentCardActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest setActiveRequest) {
        resourceSecurity.requireAdminOrCardOwner(id);
        paymentCardService.setPaymentCardActive(id, setActiveRequest.active());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deletePaymentCard(@PathVariable UUID id) {
        resourceSecurity.requireAdminOrCardOwner(id);
        paymentCardService.deletePaymentCard(id);
        return ResponseEntity.noContent().build();
    }
}
