package inno.user_service.service;

import inno.user_service.dao.repository.PaymentCardRepository;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.dao.specification.PaymentCardSpecification;
import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.entity.PaymentCard;
import inno.user_service.entity.User;
import inno.user_service.exception.CardLimitExceededException;
import inno.user_service.exception.PaymentCardNotFoundException;
import inno.user_service.exception.UserNotFoundException;
import inno.user_service.mapper.PaymentCardMapper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCardService {
    private static final int MAX_CARDS_PER_USER = 5;

    private final PaymentCardRepository paymentCardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper paymentCardMapper;

    public PaymentCardResponse createPaymentCard(UUID userId, CreatePaymentCardRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (paymentCardRepository.countByUserId(userId) >= MAX_CARDS_PER_USER) {
            throw new CardLimitExceededException(userId);
        }

        PaymentCard card = paymentCardMapper.toEntity(request);
        card.setUser(user);
        return paymentCardMapper.toResponse(paymentCardRepository.save(card));
    }

    public PaymentCardResponse getPaymentCardById(UUID id) {
        PaymentCard card = paymentCardRepository.findById(id)
                .orElseThrow(() -> new PaymentCardNotFoundException(id));
        return paymentCardMapper.toResponse(card);
    }

    public Page<PaymentCardResponse> getAllPaymentCards(String username, String surname, Pageable pageable) {
        return paymentCardRepository.findAll(PaymentCardSpecification.filterByOwner(username, surname), pageable)
                .map(paymentCardMapper::toResponse);
    }

    public Page<PaymentCardResponse> getPaymentCardsByUserId(UUID userId, Pageable pageable) {
        return paymentCardRepository.findAllByUserId(userId, pageable)
                .map(paymentCardMapper::toResponse);
    }

    @Transactional
    public PaymentCardResponse updatePaymentCard(UUID id, UpdatePaymentCardRequest request) {
        int updated = paymentCardRepository.updateCardDetails(
                id, request.number(), request.holder(), request.expirationDate());
        if (updated == 0) {
            throw new PaymentCardNotFoundException(id);
        }
        return getPaymentCardById(id);
    }

    @Transactional
    public void setPaymentCardActive(UUID id, boolean active) {
        int updated = paymentCardRepository.setActive(id, active);
        if (updated == 0) {
            throw new PaymentCardNotFoundException(id);
        }
    }

    @Transactional
    public void deletePaymentCard(UUID id) {
        if (!paymentCardRepository.existsById(id)) {
            throw new PaymentCardNotFoundException(id);
        }
        paymentCardRepository.deleteById(id);
    }
}
