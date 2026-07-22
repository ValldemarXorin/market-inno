package inno.user_service.repository;

import inno.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    boolean existsByEmail(String email);

    // ===== 1. Create =====
    // Используется унаследованный save(User) из JpaRepository

    // ===== 2. Get by id =====
    // Используется унаследованный findById(UUID) из JpaRepository

    Optional<User> findByIdAndActiveTrue(UUID id);

    // ===== 3. Get all с пагинацией и фильтром по name/surname (Specifications) =====
    // Используется унаследованный findAll(Specification<User>, Pageable)
    // из JpaSpecificationExecutor, вместе с UserSpecifications.filterBy(name, surname)

    // ===== 5. Update by id =====
    // Используется унаследованный findById(UUID) для загрузки + save(User) для сохранения —
    // dirty checking через изменённые сеттеры сам сгенерирует UPDATE,
    // JPA Auditing (@LastModifiedDate) сработает автоматически

    // ===== 6. Activate/Deactivate =====
    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET active = :active, updated_at = now() WHERE id = :id", nativeQuery = true)
    int setActiveNative(@Param("id") UUID id, @Param("active") boolean active);
}
