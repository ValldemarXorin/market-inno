package inno.user_service.service;

import inno.user_service.config.CacheNames;
import inno.user_service.config.RedisConfig;
import inno.user_service.dao.repository.PaymentCardRepository;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.dao.specification.PaymentCardSpecification;
import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.entity.PaymentCard;
import inno.user_service.entity.User;
import inno.user_service.exception.custom_exception.CardLimitExceededException;
import inno.user_service.exception.custom_exception.PaymentCardNotFoundException;
import inno.user_service.exception.custom_exception.UserNotFoundException;
import inno.user_service.mapper.PaymentCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCardService {

    private static final int MAX_CARDS_PER_USER = 5;

    private final PaymentCardRepository paymentCardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper paymentCardMapper;
    private final CacheManager cacheManager;

    @CacheEvict(cacheNames = CacheNames.USER_CARDS_CACHE, key = "#userId")
    public PaymentCardResponse createPaymentCard(UUID userId, CreatePaymentCardRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (paymentCardRepository.countByUserId(userId) >= MAX_CARDS_PER_USER) {
            throw new CardLimitExceededException(userId);
        }

        PaymentCard card = paymentCardMapper.toEntity(request);
        card.setUser(user);
        PaymentCard saved = paymentCardRepository.save(card);
        return paymentCardMapper.toResponse(saved);
    }

    public PaymentCardResponse getPaymentCardById(UUID id) {
        PaymentCard card = paymentCardRepository.findById(id)
                .orElseThrow(() -> new PaymentCardNotFoundException(id));
        return paymentCardMapper.toResponse(card);
    }

    public Page<PaymentCardResponse> getAllPaymentCards(String name, String surname, Pageable pageable) {
        return paymentCardRepository.findAll(PaymentCardSpecification.filterByOwner(name, surname), pageable)
                .map(paymentCardMapper::toResponse);
    }

    public Page<PaymentCardResponse> getPaymentCardsByUserId(UUID userId, Pageable pageable) {
        return paymentCardRepository.findAllByUserId(userId, pageable)
                .map(paymentCardMapper::toResponse);
    }

    @Cacheable(cacheNames = CacheNames.USER_CARDS_CACHE, key = "#userId")
    public List<PaymentCardResponse> getAllCardsByUserId(UUID userId) {
        return paymentCardRepository.findAllByUserId(userId).stream()
                .map(paymentCardMapper::toResponse)
                .toList();
    }

    @Transactional
    public PaymentCardResponse updatePaymentCard(UUID id, UpdatePaymentCardRequest request) {
        int updated = paymentCardRepository.updateCardDetails(
                id, request.number(), request.holder(), request.expirationDate());
        if (updated == 0) {
            throw new PaymentCardNotFoundException(id);
        }
        evictOwnerCardsCache(id);
        return getPaymentCardById(id);
    }

    @Transactional
    public void setPaymentCardActive(UUID id, boolean active) {
        int updated = paymentCardRepository.setActive(id, active);
        if (updated == 0) {
            throw new PaymentCardNotFoundException(id);
        }
        evictOwnerCardsCache(id);
    }

    @Transactional
    public void deletePaymentCard(UUID id) {
        UUID ownerId = paymentCardRepository.findUserIdById(id)
                .orElseThrow(() -> new PaymentCardNotFoundException(id));

        paymentCardRepository.deleteById(id);
        evictCardsCache(ownerId);
    }

    private void evictOwnerCardsCache(UUID cardId) {
        paymentCardRepository.findUserIdById(cardId).ifPresent(this::evictCardsCache);
    }

    private void evictCardsCache(UUID userId) {
        var cache = cacheManager.getCache(CacheNames.USER_CARDS_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
