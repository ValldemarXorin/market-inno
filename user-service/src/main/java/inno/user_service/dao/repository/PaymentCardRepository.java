package inno.user_service.dao.repository;

import inno.user_service.entity.PaymentCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID>, JpaSpecificationExecutor<PaymentCard> {

    long countByUserId(UUID userId);

    // ===== 1. Create =====
    // Используется унаследованный save(PaymentCard) из JpaRepository.
    // Проверку лимита 5 карт Service делает через countByUserId() перед вызовом save()

    // ===== 2. Get by id =====
    // Используется унаследованный findById(UUID) из JpaRepository

    Optional<PaymentCard> findByIdAndActiveTrue(UUID id);

    // ===== 3. Get all с пагинацией и фильтром по владельцу (Specifications) =====
    // Используется унаследованный findAll(Specification<PaymentCard>, Pageable)
    // из JpaSpecificationExecutor, вместе с PaymentCardSpecifications.filterByOwner(...)

    List<PaymentCard> findAllByUserId(UUID userId);

    Page<PaymentCard> findAllByUserId(UUID userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
            UPDATE PaymentCard c
            SET c.number = :number,
                c.holder = :holder,
                c.expirationDate = :expirationDate,
                c.updatedAt = CURRENT_TIMESTAMP
            WHERE c.id = :id
            """)
    int updateCardDetails(@Param("id") UUID id,
                          @Param("number") String number,
                          @Param("holder") String holder,
                          @Param("expirationDate") LocalDate expirationDate);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentCard c SET c.active = :active, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    int setActive(@Param("id") UUID id, @Param("active") boolean active);
}
