package com.mfg.ims.doccore.approvalrule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ApprovalRuleStepRepository extends JpaRepository<ApprovalRuleStep, Long> {
    List<ApprovalRuleStep> findByRuleIdOrderByStepNo(Long ruleId);

    @Modifying
    @Query("DELETE FROM ApprovalRuleStep s WHERE s.ruleId = :ruleId")
    void deleteByRuleId(Long ruleId);
}
