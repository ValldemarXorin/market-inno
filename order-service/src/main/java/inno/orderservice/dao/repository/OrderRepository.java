package inno.orderservice.dao.repository;

import inno.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndDeletedFalse(UUID id);

    @Modifying
    @Transactional
    @Query("""
            UPDATE Order o
            SET o.deleted = true,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :id AND o.deleted = false
            """)
    int softDeleteById(@Param("id") UUID id);
}