package com.mfg.ims.crm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.crm.domain.CrmLead;
import com.mfg.ims.crm.domain.CrmOpportunity;
import com.mfg.ims.crm.domain.LeadStatus;
import com.mfg.ims.crm.domain.OpportunityStage;
import com.mfg.ims.crm.dto.AdvanceOpportunityRequest;
import com.mfg.ims.crm.dto.OpportunityDto;
import com.mfg.ims.crm.dto.SaveOpportunityRequest;
import com.mfg.ims.crm.repository.CrmLeadRepository;
import com.mfg.ims.crm.repository.CrmOpportunityRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class CrmOpportunityService {

    private final CrmOpportunityRepository repo;
    private final CrmLeadRepository leadRepo;
    private final DocumentNumberService numberService;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CrmOpportunityService(CrmOpportunityRepository repo, CrmLeadRepository leadRepo,
                                 DocumentNumberService numberService, CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.leadRepo = leadRepo;
        this.numberService = numberService;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<OpportunityDto> search(String stage, Long customerId, Long ownerId, String q, Pageable pageable) {
        Specification<CrmOpportunity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (stage != null && !stage.isBlank()) p.add(cb.equal(root.get("stage"), stage));
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (ownerId != null) p.add(cb.equal(root.get("ownerId"), ownerId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.<String>get("description")), like),
                        cb.like(cb.lower(root.<String>get("opportunityNumber")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public OpportunityDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public OpportunityDto create(SaveOpportunityRequest req) {
        CrmLead lead = null;
        if (req.leadId() != null) {
            lead = leadRepo.findById(req.leadId()).orElseThrow(() -> new NotFoundException("Lead", req.leadId()));
            if (LeadStatus.UNQUALIFIED.name().equals(lead.getStatus())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Lead " + lead.getLeadNumber() + " is unqualified and cannot have opportunities");
            }
        }
        Long customerId = req.customerId() != null ? req.customerId()
                : lead != null ? lead.getConvertedCustomerId() : null;
        if (lead == null && customerId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An opportunity needs a lead or a customer");
        }
        if (customerId != null) lookups.requireCustomer(customerId);
        if (req.contactId() != null) lookups.requireContact(req.contactId());

        CrmOpportunity o = new CrmOpportunity();
        o.setOpportunityNumber(numberService.next("OPP"));
        o.setLeadId(req.leadId());
        o.setCustomerId(customerId);
        o.setContactId(req.contactId());
        o.setDescription(req.description());
        o.setStage(OpportunityStage.PROSPECT.name());
        o.setProbabilityPct(req.probabilityPct() != null ? req.probabilityPct() : OpportunityStage.PROSPECT.defaultProbability());
        o.setEstimatedValue(req.estimatedValue());
        o.setExpectedCloseDate(req.expectedCloseDate());
        o.setOwnerId(lookups.ownerOrCurrent(req.ownerId()));
        o = repo.save(o);
        auditService.record("OPPORTUNITY_CREATE", "CRM_OPPORTUNITY", String.valueOf(o.getId()), null,
                o.getOpportunityNumber(), null);
        return toDto(o);
    }

    /** Called when a qualified lead is converted: the opportunity inherits the lead's contact, value and owner. */
    @Transactional
    public OpportunityDto createForLead(CrmLead lead, Long customerId, String description) {
        CrmOpportunity o = new CrmOpportunity();
        o.setOpportunityNumber(numberService.next("OPP"));
        o.setLeadId(lead.getId());
        o.setCustomerId(customerId);
        o.setContactId(lead.getContactId());
        String text = description != null && !description.isBlank() ? description : "Opportunity from " + lead.getLeadNumber();
        o.setDescription(text.length() > 200 ? text.substring(0, 200) : text);
        o.setEstimatedValue(lead.getEstimatedValue());
        o.setExpectedCloseDate(lead.getExpectedCloseDate());
        o.setOwnerId(lead.getOwnerId());
        o = repo.save(o);
        auditService.record("OPPORTUNITY_CREATE", "CRM_OPPORTUNITY", String.valueOf(o.getId()), null,
                o.getOpportunityNumber() + " from " + lead.getLeadNumber(), null);
        return toDto(o);
    }

    @Transactional
    public OpportunityDto update(Long id, SaveOpportunityRequest req) {
        CrmOpportunity o = require(id);
        CrmLookups.checkVersion(req.version(), o.getVersion(), "Opportunity");
        if (!OpportunityStage.valueOf(o.getStage()).isOpen()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + o.getStage() + " opportunity can no longer be edited");
        }
        if (req.customerId() != null) lookups.requireCustomer(req.customerId());
        if (req.contactId() != null) lookups.requireContact(req.contactId());
        String old = o.getDescription() + " / " + o.getEstimatedValue();
        if (req.customerId() != null) o.setCustomerId(req.customerId());
        o.setContactId(req.contactId());
        o.setDescription(req.description());
        if (req.probabilityPct() != null) o.setProbabilityPct(req.probabilityPct());
        o.setEstimatedValue(req.estimatedValue());
        o.setExpectedCloseDate(req.expectedCloseDate());
        if (req.ownerId() != null) o.setOwnerId(lookups.ownerOrCurrent(req.ownerId()));
        o = repo.save(o);
        auditService.record("OPPORTUNITY_UPDATE", "CRM_OPPORTUNITY", String.valueOf(id), old,
                o.getDescription() + " / " + o.getEstimatedValue(), null);
        return toDto(o);
    }

    @Transactional
    public OpportunityDto advance(Long id, AdvanceOpportunityRequest req) {
        CrmOpportunity o = require(id);
        OpportunityStage current = OpportunityStage.valueOf(o.getStage());
        OpportunityStage target;
        try {
            target = OpportunityStage.valueOf(req.stage());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown stage: " + req.stage());
        }
        if (!current.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move an opportunity from " + current + " to " + target);
        }
        if (target == OpportunityStage.CLOSED_LOST && (req.lostReason() == null || req.lostReason().isBlank())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A lost reason is required to close as lost");
        }
        move(o, current, target, req.lostReason());
        o = repo.save(o);
        return toDto(o);
    }

    /**
     * A quotation for this opportunity was converted to a sales order: link it, and close the opportunity as won
     * when it is far enough along (PROPOSAL or NEGOTIATION).
     */
    @Transactional
    public void recordConvertedQuotation(Long opportunityId, Long quotationId) {
        CrmOpportunity o = require(opportunityId);
        o.setConvertedQuotationId(quotationId);
        OpportunityStage current = OpportunityStage.valueOf(o.getStage());
        if (current.canMoveTo(OpportunityStage.CLOSED_WON)) {
            move(o, current, OpportunityStage.CLOSED_WON, null);
        }
        repo.save(o);
    }

    private void move(CrmOpportunity o, OpportunityStage from, OpportunityStage to, String lostReason) {
        o.setStage(to.name());
        o.setProbabilityPct(to.defaultProbability());
        if (to == OpportunityStage.CLOSED_WON) o.setWonAt(Instant.now());
        if (to == OpportunityStage.CLOSED_LOST) o.setLostReason(lostReason);
        auditService.record("OPPORTUNITY_STAGE", "CRM_OPPORTUNITY", String.valueOf(o.getId()), from.name(), to.name(),
                to == OpportunityStage.CLOSED_LOST ? lostReason : null);
    }

    private CrmOpportunity require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Opportunity", id));
    }

    private OpportunityDto toDto(CrmOpportunity o) {
        OpportunityStage stage = OpportunityStage.valueOf(o.getStage());
        List<String> actions = new ArrayList<>();
        if (stage.isOpen()) actions.add("EDIT");
        stage.next().stream().sorted().forEach(s -> actions.add(s.name()));
        BigDecimal weighted = o.getEstimatedValue() == null ? null
                : o.getEstimatedValue().multiply(BigDecimal.valueOf(o.getProbabilityPct()))
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return new OpportunityDto(o.getId(), o.getOpportunityNumber(), o.getLeadId(), o.getCustomerId(),
                lookups.customerName(o.getCustomerId()), o.getContactId(), lookups.contactName(o.getContactId()),
                o.getDescription(), o.getStage(), o.getProbabilityPct(), o.getEstimatedValue(), weighted,
                o.getExpectedCloseDate(), o.getOwnerId(), lookups.userName(o.getOwnerId()),
                o.getConvertedQuotationId(), o.getWonAt(), o.getLostReason(), o.getVersion(), actions);
    }
}
