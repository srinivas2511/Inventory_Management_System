package com.mfg.ims.iam.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.iam.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    Set<Permission> findByCodeIn(Set<String> codes);
}
