package inno.paymentservice.dao.repository;

import inno.paymentservice.entity.Payment;
import inno.paymentservice.entity.PaymentStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, UUID> {

    List<Payment> findByUserId(UUID userId);

    List<Payment> findByOrderId(UUID orderId);

    List<Payment> findByStatus(PaymentStatus status);

    @Aggregation(pipeline = {
            "{ '$match': { 'userId': ?0, 'timestamp': { '$gte': ?1, '$lte': ?2 } } }",
            "{ '$group': { '_id': null, 'total': { '$sum': '$paymentAmount' } } }"
    })
    List<Total> findTotalByUserIdAndTimestampBetween(UUID userId,
                                                     LocalDateTime start,
                                                     LocalDateTime end);

    @Aggregation(pipeline = {
            "{ '$match': { 'timestamp': { '$gte': ?0, '$lte': ?1 } } }",
            "{ '$group': { '_id': null, 'total': { '$sum': '$paymentAmount' } } }"
    })
    List<Total> findTotalByTimestampBetween(LocalDateTime start, LocalDateTime end);

    interface Total {
        Double getTotal();
    }
}