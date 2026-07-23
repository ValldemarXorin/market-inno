package inno.user_service.service;

import inno.user_service.dao.repository.PaymentCardRepository;
import inno.user_service.dao.repository.UserRepository;
import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.entity.PaymentCard;
import inno.user_service.entity.User;
import inno.user_service.exception.custom_exception.CardLimitExceededException;
import inno.user_service.exception.custom_exception.UserNotFoundException;
import inno.user_service.mapper.PaymentCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceTest {

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentCardMapper paymentCardMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache mockCache;

    @InjectMocks
    private PaymentCardService paymentCardService;

    private UUID testUserId;
    private UUID testCardId;
    private User testUser;
    private PaymentCard testCard;
    private PaymentCardResponse testCardResponse;
    private String testCardNumber = "1111222233334444";
    private String testHolder = "VOVA KHORIN";
    private LocalDate testExpDate = LocalDate.now().plusYears(3);

    @BeforeEach
    public void initData() {
        testUserId = UUID.randomUUID();
        testCardId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(testUserId);

        testCard = new PaymentCard();
        testCard.setId(testCardId);
        testCard.setUser(testUser);

        testCardResponse = new PaymentCardResponse(
                testCardId,
                testUserId,
                testCardNumber,
                testHolder,
                testExpDate,
                true,
                null,
                null
        );
    }

    @Test
    public void shouldCreatePaymentCardSuccessfully() {
        CreatePaymentCardRequest createRequest = new CreatePaymentCardRequest(
                testCardNumber, testHolder, testExpDate);

        doReturn(Optional.of(testUser)).when(userRepository).findById(testUserId);
        doReturn(3L).when(paymentCardRepository).countByUserId(testUserId); // лимит 5, так что 3 — ок
        doReturn(testCard).when(paymentCardMapper).toEntity(createRequest);
        doReturn(testCard).when(paymentCardRepository).save(testCard);
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        PaymentCardResponse result = paymentCardService.createPaymentCard(testUserId, createRequest);

        assertNotNull(result);
        assertEquals(testCardNumber, result.number());
        assertEquals(testCardId, result.id());
    }

    @Test
    public void shouldThrowExceptionWhenUserNotFoundDuringCardCreation() {
        CreatePaymentCardRequest createRequest = new CreatePaymentCardRequest(
                testCardNumber, testHolder, testExpDate);

        doReturn(Optional.empty()).when(userRepository).findById(testUserId);

        assertThrows(UserNotFoundException.class, () -> {
            paymentCardService.createPaymentCard(testUserId, createRequest);
        });

        verify(paymentCardRepository, never()).save(any());
    }

    @Test
    public void shouldThrowExceptionWhenCardLimitExceeded() {
        CreatePaymentCardRequest createRequest = new CreatePaymentCardRequest(
                testCardNumber, testHolder, testExpDate);

        doReturn(Optional.of(testUser)).when(userRepository).findById(testUserId);
        doReturn(5L).when(paymentCardRepository).countByUserId(testUserId); // лимит уже достигнут

        assertThrows(CardLimitExceededException.class, () -> {
            paymentCardService.createPaymentCard(testUserId, createRequest);
        });

        verify(paymentCardRepository, never()).save(any());
    }

    @Test
    public void shouldGetPaymentCardByIdSuccessfully() {
        doReturn(Optional.of(testCard)).when(paymentCardRepository).findById(testCardId);
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        PaymentCardResponse result = paymentCardService.getPaymentCardById(testCardId);

        assertNotNull(result);
        assertEquals(testCardId, result.id());
    }

    @Test
    public void shouldGetAllPaymentCardsWithFilters() {
        List<PaymentCard> cardList = new ArrayList<>();
        cardList.add(testCard);
        Page<PaymentCard> cardPage = new PageImpl<>(cardList);

        doReturn(cardPage).when(paymentCardRepository).findAll(any(Specification.class), any(Pageable.class));
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        Page<PaymentCardResponse> resultPage = paymentCardService.getAllPaymentCards("vova", "khorin", Pageable.unpaged());

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements());
    }

    @Test
    public void shouldGetPaymentCardsByUserIdWithPagination() {
        List<PaymentCard> cardList = new ArrayList<>();
        cardList.add(testCard);
        Page<PaymentCard> cardPage = new PageImpl<>(cardList);

        doReturn(cardPage).when(paymentCardRepository).findAllByUserId(testUserId, Pageable.unpaged());
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        Page<PaymentCardResponse> resultPage = paymentCardService.getPaymentCardsByUserId(testUserId, Pageable.unpaged());

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements());
    }

    @Test
    public void shouldGetAllCardsByUserIdWithoutPagination() {
        List<PaymentCard> cardList = new ArrayList<>();
        cardList.add(testCard);

        doReturn(cardList).when(paymentCardRepository).findAllByUserId(testUserId);
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        List<PaymentCardResponse> resultList = paymentCardService.getAllCardsByUserId(testUserId);

        assertNotNull(resultList);
        assertEquals(1, resultList.size());
    }

    @Test
    public void shouldUpdatePaymentCardSuccessfully() {
        UpdatePaymentCardRequest updateRequest = new UpdatePaymentCardRequest(
                "5555666677778888", "NEW HOLDER", testExpDate);

        doReturn(1).when(paymentCardRepository).updateCardDetails(any(), any(), any(), any());
        doReturn(Optional.of(testUserId)).when(paymentCardRepository).findUserIdById(testCardId);
        doReturn(mockCache).when(cacheManager).getCache(any());
        doReturn(Optional.of(testCard)).when(paymentCardRepository).findById(testCardId);
        doReturn(testCardResponse).when(paymentCardMapper).toResponse(testCard);

        PaymentCardResponse result = paymentCardService.updatePaymentCard(testCardId, updateRequest);

        assertNotNull(result);
    }

    @Test
    public void shouldSetPaymentCardActiveStatusSuccessfully() {
        doReturn(1).when(paymentCardRepository).setActive(testCardId, false);
        doReturn(Optional.of(testUserId)).when(paymentCardRepository).findUserIdById(testCardId);
        doReturn(mockCache).when(cacheManager).getCache(any());

        assertDoesNotThrow(() -> {
            paymentCardService.setPaymentCardActive(testCardId, false);
        });
    }

    @Test
    public void shouldDeletePaymentCardSuccessfully() {
        doReturn(Optional.of(testUserId)).when(paymentCardRepository).findUserIdById(testCardId);
        doReturn(mockCache).when(cacheManager).getCache(any());

        assertDoesNotThrow(() -> {
            paymentCardService.deletePaymentCard(testCardId);
        });

        verify(paymentCardRepository, times(1)).deleteById(testCardId);
    }

    @Test
    public void shouldCheckCardCacheEvictOnDeleteSuccessfully() {
        doReturn(Optional.of(testUserId)).when(paymentCardRepository).findUserIdById(testCardId);
        doReturn(mockCache).when(cacheManager).getCache(any());

        paymentCardService.deletePaymentCard(testCardId);

        verify(mockCache, times(1)).evict(testUserId);
    }
}
