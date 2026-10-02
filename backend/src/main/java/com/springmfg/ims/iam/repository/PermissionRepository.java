package com.springmfg.ims.iam.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.iam.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    Set<Permission> findByCodeIn(Set<String> codes);
}
