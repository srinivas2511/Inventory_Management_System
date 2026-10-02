package com.springmfg.ims.finance.gl.repository;

import com.springmfg.ims.finance.gl.domain.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GlAccountRepository extends JpaRepository<GlAccount, Long> {
    Optional<GlAccount> findByAccountCode(String accountCode);
    List<GlAccount> findByActiveTrue();
}
