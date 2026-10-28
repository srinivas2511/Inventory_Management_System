package com.mfg.ims.doccore.approvalrule;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;

@Service
public class ApprovalRuleService {

    private final ApprovalRuleRepository ruleRepo;
    private final ApprovalRuleStepRepository stepRepo;

    public ApprovalRuleService(ApprovalRuleRepository ruleRepo, ApprovalRuleStepRepository stepRepo) {
        this.ruleRepo = ruleRepo;
        this.stepRepo = stepRepo;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRuleDto> list(String documentType) {
        List<ApprovalRule> rules = documentType != null
                ? ruleRepo.findByCompanyIdAndDocumentTypeAndActiveTrueOrderByPriority(1L, documentType)
                : ruleRepo.findAll();
        return rules.stream()
                .map(r -> ApprovalRuleDto.from(r, stepRepo.findByRuleIdOrderByStepNo(r.getId())))
                .toList();
    }

    @Transactional
    public ApprovalRuleDto create(SaveApprovalRuleRequest req) {
        ApprovalRule r = new ApprovalRule();
        apply(r, req);
        r = ruleRepo.save(r);
        List<ApprovalRuleStep> steps = saveSteps(r.getId(), req.steps());
        return ApprovalRuleDto.from(r, steps);
    }

    @Transactional
    public ApprovalRuleDto update(Long id, SaveApprovalRuleRequest req) {
        ApprovalRule r = ruleRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Approval rule not found: " + id));
        apply(r, req);
        ruleRepo.save(r);
        stepRepo.deleteByRuleId(id);
        List<ApprovalRuleStep> steps = saveSteps(id, req.steps());
        return ApprovalRuleDto.from(r, steps);
    }

    @Transactional
    public void deactivate(Long id) {
        ApprovalRule r = ruleRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Approval rule not found: " + id));
        r.setActive(false);
        ruleRepo.save(r);
    }

    private void apply(ApprovalRule r, SaveApprovalRuleRequest req) {
        r.setDocumentType(req.documentType().toUpperCase().trim());
        r.setRuleName(req.ruleName().trim());
        r.setConditionExpr(req.conditionExpr());
        r.setPriority(req.priority());
    }

    private List<ApprovalRuleStep> saveSteps(Long ruleId, List<SaveApprovalRuleRequest.StepRequest> stepReqs) {
        return stepReqs.stream().map(sr -> {
            ApprovalRuleStep s = new ApprovalRuleStep();
            s.setRuleId(ruleId);
            s.setStepNo(sr.stepNo());
            s.setStepGroup(sr.stepGroup());
            s.setApproverType(sr.approverType());
            s.setApproverRef(sr.approverRef());
            s.setRequiredPermission(sr.requiredPermission());
            return stepRepo.save(s);
        }).toList();
    }
}
