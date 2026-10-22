package com.mfg.ims.finance.gl.repository;

import com.mfg.ims.finance.gl.domain.GlAccountGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlAccountGroupRepository extends JpaRepository<GlAccountGroup, Long> {
    Optional<GlAccountGroup> findByGroupCode(String groupCode);
}
