package com.springmfg.ims.finance.gl.repository;

import com.springmfg.ims.finance.gl.domain.InventoryAccountAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryAccountAssignmentRepository extends JpaRepository<InventoryAccountAssignment, Long> {

    Optional<InventoryAccountAssignment> findByCompanyIdAndTransactionTypeAndItemType(
            Long companyId, String transactionType, String itemType);

    Optional<InventoryAccountAssignment> findByCompanyIdAndTransactionType(
            Long companyId, String transactionType);
}
