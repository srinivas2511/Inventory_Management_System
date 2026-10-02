package com.springmfg.ims.finance.bank.repository;

import com.springmfg.ims.finance.bank.domain.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {
    List<BankStatementLine> findByBankStatementId(Long bankStatementId);
    List<BankStatementLine> findByBankStatementIdAndReconciled(Long bankStatementId, boolean reconciled);
}
