package inno.authservice.repository;

import inno.authservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop10ByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query("""
            UPDATE OutboxEvent o
            SET o.published = true,
                o.publishedAt = CURRENT_TIMESTAMP,
                o.attempts = o.attempts + 1,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :id AND o.published = false
            """)
    int markPublished(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("""
            UPDATE OutboxEvent o
            SET o.attempts = o.attempts + 1,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.id = :id
            """)
    int incrementAttempts(@Param("id") UUID id);
}
