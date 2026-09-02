package inno.paymentservice.dao.repository;

import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByUserId(UUID userId);

    List<Payment> findByOrderId(UUID orderId);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("""
            select coalesce(sum(p.paymentAmount), 0)
            from Payment p
            where p.userId = :userId and p.timestamp >= :start and p.timestamp <= :end
            """)
    BigDecimal sumPaymentAmountByUserIdAndTimestampBetween(@Param("userId") UUID userId,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(p.paymentAmount), 0)
            from Payment p
            where p.timestamp >= :start and p.timestamp <= :end
            """)
    BigDecimal sumPaymentAmountByTimestampBetween(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}