package com.springmfg.ims.iam.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.iam.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
           "(:search = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
           "AND (:active IS NULL OR u.active = :active)")
    Page<User> searchNormalised(@Param("search") String search,
                      @Param("active") Boolean active,
                      Pageable pageable);

    /** Active administrators other than {@code excludedId} whose account is not locked at {@code now}. */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.code = 'ADMIN' AND u.active = true "
            + "AND u.id <> :excludedId AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)")
    long countActiveUnlockedAdminsExcept(@Param("excludedId") Long excludedId,
                                         @Param("now") java.time.Instant now);

    /** Serialises changes that can remove an administrator; released when the transaction ends. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(7101)) AS admin_invariant_lock",
           nativeQuery = true)
    Integer lockAdminInvariant();

    default Page<User> search(String search, Boolean active, Pageable pageable) {
        return searchNormalised(SearchText.normalise(search), active, pageable);
    }

    /** Active users who hold a permission through any of their roles (notification routing). */
    @Query("SELECT DISTINCT u.id FROM User u JOIN u.roles r JOIN r.permissions p "
            + "WHERE u.active = true AND p.code = :code")
    java.util.List<Long> findActiveIdsByPermission(@org.springframework.data.repository.query.Param("code") String code);
}
