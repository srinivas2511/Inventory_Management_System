package com.springmfg.ims.iam;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Row-locked lookup so concurrent failed logins cannot lose a count. Usernames are case-insensitive. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCode(String employeeCode);

    /** Active users holding the ADMIN role other than {@code excludedUserId}. */
    @Query("select count(u) from User u join u.roles r where r.code = 'ADMIN' and u.active = true and u.id <> :excludedUserId")
    long countOtherActiveAdmins(@Param("excludedUserId") Long excludedUserId);

    /**
     * Invalidates the tokens of everyone holding the role after its permissions changed. A bulk update skips
     * {@code @Version}, so the row version is advanced explicitly.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update User u set u.permissionVersion = u.permissionVersion + 1, u.version = u.version + 1,
                u.updatedAt = :now
            where u.id in (select u2.id from User u2 join u2.roles r where r.id = :roleId)""")
    int bumpPermissionVersionForRole(@Param("roleId") Long roleId, @Param("now") Instant now);
}
