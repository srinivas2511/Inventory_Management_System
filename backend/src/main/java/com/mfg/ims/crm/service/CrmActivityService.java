package com.mfg.ims.crm.service;

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
import com.mfg.ims.crm.domain.ActivityType;
import com.mfg.ims.crm.domain.CrmActivity;
import com.mfg.ims.crm.domain.CrmContact;
import com.mfg.ims.crm.domain.CrmLead;
import com.mfg.ims.crm.domain.CrmOpportunity;
import com.mfg.ims.crm.dto.ActivityDto;
import com.mfg.ims.crm.dto.SaveActivityRequest;
import com.mfg.ims.crm.repository.CrmActivityRepository;
import com.mfg.ims.crm.repository.CrmLeadRepository;
import com.mfg.ims.crm.repository.CrmOpportunityRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * The customer activity log (CRM-003). The customer is stored on every activity, derived from the contact,
 * opportunity or lead when not given, so "all activity for a customer" is a single indexed filter.
 */
@Service
public class CrmActivityService {

    private final CrmActivityRepository repo;
    private final CrmLeadRepository leadRepo;
    private final CrmOpportunityRepository oppRepo;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CrmActivityService(CrmActivityRepository repo, CrmLeadRepository leadRepo, CrmOpportunityRepository oppRepo,
                              CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.leadRepo = leadRepo;
        this.oppRepo = oppRepo;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ActivityDto> search(Long customerId, Long contactId, Long leadId, Long opportunityId, String type,
                                    Instant from, Instant to, Pageable pageable) {
        Specification<CrmActivity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (contactId != null) p.add(cb.equal(root.get("contactId"), contactId));
            if (leadId != null) p.add(cb.equal(root.get("leadId"), leadId));
            if (opportunityId != null) p.add(cb.equal(root.get("opportunityId"), opportunityId));
            if (type != null && !type.isBlank()) p.add(cb.equal(root.get("activityType"), type));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<Instant>get("activityDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.<Instant>get("activityDate"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ActivityDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public ActivityDto create(SaveActivityRequest req) {
        CrmActivity a = new CrmActivity();
        apply(a, req);
        a = repo.save(a);
        auditService.record("ACTIVITY_CREATE", "CRM_ACTIVITY", String.valueOf(a.getId()), null,
                a.getActivityType() + ": " + a.getSubject(), null);
        return toDto(a);
    }

    @Transactional
    public ActivityDto update(Long id, SaveActivityRequest req) {
        CrmActivity a = require(id);
        CrmLookups.checkVersion(req.version(), a.getVersion(), "Activity");
        String old = a.getActivityType() + ": " + a.getSubject();
        apply(a, req);
        a = repo.save(a);
        auditService.record("ACTIVITY_UPDATE", "CRM_ACTIVITY", String.valueOf(id), old,
                a.getActivityType() + ": " + a.getSubject(), null);
        return toDto(a);
    }

    @Transactional
    public void delete(Long id) {
        CrmActivity a = require(id);
        repo.delete(a);
        auditService.record("ACTIVITY_DELETE", "CRM_ACTIVITY", String.valueOf(id),
                a.getActivityType() + ": " + a.getSubject(), null, null);
    }

    private void apply(CrmActivity a, SaveActivityRequest req) {
        ActivityType type;
        try {
            type = ActivityType.valueOf(req.activityType());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "activityType must be one of CALL, EMAIL, MEETING, DEMO, FOLLOW_UP");
        }
        if (req.customerId() == null && req.contactId() == null && req.leadId() == null && req.opportunityId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "An activity must reference a customer, contact, lead or opportunity");
        }
        Long customerId = req.customerId();
        if (customerId != null) lookups.requireCustomer(customerId);
        if (req.contactId() != null) {
            CrmContact contact = lookups.requireContact(req.contactId());
            if (customerId == null) customerId = contact.getCustomerId();
        }
        if (req.opportunityId() != null) {
            CrmOpportunity opp = oppRepo.findById(req.opportunityId())
                    .orElseThrow(() -> new NotFoundException("Opportunity", req.opportunityId()));
            if (customerId == null) customerId = opp.getCustomerId();
        }
        if (req.leadId() != null) {
            CrmLead lead = leadRepo.findById(req.leadId())
                    .orElseThrow(() -> new NotFoundException("Lead", req.leadId()));
            if (customerId == null) customerId = lead.getConvertedCustomerId();
        }
        a.setActivityType(type.name());
        a.setCustomerId(customerId);
        a.setContactId(req.contactId());
        a.setLeadId(req.leadId());
        a.setOpportunityId(req.opportunityId());
        a.setSubject(req.subject());
        a.setNotes(req.notes());
        a.setOutcome(req.outcome());
        a.setActivityDate(req.activityDate() != null ? req.activityDate() : Instant.now());
        a.setDurationMinutes(req.durationMinutes());
        a.setOwnerId(lookups.ownerOrCurrent(req.ownerId() != null ? req.ownerId() : a.getOwnerId()));
    }

    private CrmActivity require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Activity", id));
    }

    private ActivityDto toDto(CrmActivity a) {
        return new ActivityDto(a.getId(), a.getActivityType(), a.getCustomerId(), lookups.customerName(a.getCustomerId()),
                a.getContactId(), lookups.contactName(a.getContactId()), a.getLeadId(), a.getOpportunityId(),
                a.getSubject(), a.getNotes(), a.getOutcome(), a.getActivityDate(), a.getDurationMinutes(),
                a.getOwnerId(), lookups.userName(a.getOwnerId()), a.getVersion());
    }
}
