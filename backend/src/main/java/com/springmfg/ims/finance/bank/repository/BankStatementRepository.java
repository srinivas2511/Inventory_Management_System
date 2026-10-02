package com.springmfg.ims.finance.bank.repository;

import com.springmfg.ims.finance.bank.domain.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {
    Page<BankStatement> findByGlAccountId(Long glAccountId, Pageable pageable);
}
