package inno.user_service.dao.repository;

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

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.username = :username,
                u.surname = :surname,
                u.birthDate = :birthDate,
                u.email = :email,
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.id = :id
            """)
    int updateUserDetails(@Param("id") UUID id,
                          @Param("username") String name,
                          @Param("surname") String surname,
                          @Param("birthDate") LocalDate birthDate,
                          @Param("email") String email);

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET active = :active, updated_at = now() WHERE id = :id", nativeQuery = true)
    int setActiveNative(@Param("id") UUID id, @Param("active") boolean active);
}
