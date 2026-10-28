package com.mfg.ims.doccore.approvalrule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByCompanyIdAndDocumentTypeAndActiveTrueOrderByPriority(Long companyId, String documentType);
}
