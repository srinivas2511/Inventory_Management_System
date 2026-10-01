package com.springmfg.ims.iam;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);

    List<Role> findAllByOrderByCodeAsc();

    /** Row lock used to serialise changes that could remove the last Admin. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    /** {@code [roleId, userCount]} for every role that has users. */
    @Query("select r.id, count(u) from User u join u.roles r group by r.id")
    List<Object[]> countUsersPerRole();

    @Query("select count(u) from User u join u.roles r where r.id = :roleId")
    long countUsers(@Param("roleId") Long roleId);
}
