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
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.crm.domain.CrmContact;
import com.mfg.ims.crm.domain.CrmLead;
import com.mfg.ims.crm.domain.LeadStatus;
import com.mfg.ims.crm.dto.ConvertLeadRequest;
import com.mfg.ims.crm.dto.ConvertLeadResultDto;
import com.mfg.ims.crm.dto.LeadDto;
import com.mfg.ims.crm.dto.OpportunityDto;
import com.mfg.ims.crm.dto.SaveLeadRequest;
import com.mfg.ims.crm.repository.CrmActivityRepository;
import com.mfg.ims.crm.repository.CrmContactRepository;
import com.mfg.ims.crm.repository.CrmLeadRepository;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.dto.CustomerDto;
import com.mfg.ims.partner.dto.SaveCustomerRequest;
import com.mfg.ims.partner.service.CustomerService;

import jakarta.persistence.criteria.Predicate;

@Service
public class CrmLeadService {

    private final CrmLeadRepository repo;
    private final CrmContactRepository contactRepo;
    private final CrmActivityRepository activityRepo;
    private final CrmOpportunityService opportunityService;
    private final CustomerService customerService;
    private final DocumentNumberService numberService;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CrmLeadService(CrmLeadRepository repo, CrmContactRepository contactRepo, CrmActivityRepository activityRepo,
                          CrmOpportunityService opportunityService, CustomerService customerService,
                          DocumentNumberService numberService, CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.contactRepo = contactRepo;
        this.activityRepo = activityRepo;
        this.opportunityService = opportunityService;
        this.customerService = customerService;
        this.numberService = numberService;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<LeadDto> search(String status, Long ownerId, String q, Pageable pageable) {
        Specification<CrmLead> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null && !status.isBlank()) p.add(cb.equal(root.get("status"), status));
            if (ownerId != null) p.add(cb.equal(root.get("ownerId"), ownerId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.<String>get("companyName")), like),
                        cb.like(cb.lower(root.<String>get("leadNumber")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public LeadDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public LeadDto create(SaveLeadRequest req) {
        if (req.contactId() != null) lookups.requireContact(req.contactId());
        CrmLead lead = new CrmLead();
        lead.setLeadNumber(numberService.next("LD"));
        lead.setStatus(LeadStatus.NEW.name());
        lead.setOwnerId(lookups.ownerOrCurrent(req.ownerId()));
        apply(lead, req);
        lead = repo.save(lead);
        auditService.record("LEAD_CREATE", "CRM_LEAD", String.valueOf(lead.getId()), null, lead.getLeadNumber(), null);
        return toDto(lead);
    }

    @Transactional
    public LeadDto update(Long id, SaveLeadRequest req) {
        CrmLead lead = require(id);
        CrmLookups.checkVersion(req.version(), lead.getVersion(), "Lead");
        if (!LeadStatus.valueOf(lead.getStatus()).isOpen()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + lead.getStatus() + " lead can no longer be edited");
        }
        if (req.contactId() != null) lookups.requireContact(req.contactId());
        if (req.ownerId() != null) lead.setOwnerId(lookups.ownerOrCurrent(req.ownerId()));
        String old = lead.getCompanyName() + " / " + lead.getEstimatedValue();
        apply(lead, req);
        lead = repo.save(lead);
        auditService.record("LEAD_UPDATE", "CRM_LEAD", String.valueOf(id), old,
                lead.getCompanyName() + " / " + lead.getEstimatedValue(), null);
        return toDto(lead);
    }

    @Transactional
    public LeadDto markContacted(Long id) {
        CrmLead lead = require(id);
        transition(lead, LeadStatus.CONTACTED, "LEAD_CONTACTED", null);
        return toDto(repo.save(lead));
    }

    @Transactional
    public LeadDto qualify(Long id) {
        CrmLead lead = require(id);
        transition(lead, LeadStatus.QUALIFIED, "LEAD_QUALIFIED", null);
        return toDto(repo.save(lead));
    }

    @Transactional
    public LeadDto disqualify(Long id, String reason) {
        CrmLead lead = require(id);
        transition(lead, LeadStatus.UNQUALIFIED, "LEAD_DISQUALIFIED", reason);
        lead.setDisqualifyReason(reason);
        return toDto(repo.save(lead));
    }

    /**
     * Converts a qualified lead: attaches it to an existing customer or creates one from the lead, creates the
     * opportunity, links the contact to the customer and moves earlier lead activity into the customer's log.
     */
    @Transactional
    public ConvertLeadResultDto convertToOpportunity(Long id, ConvertLeadRequest req) {
        CrmLead lead = require(id);
        LeadStatus current = LeadStatus.valueOf(lead.getStatus());
        if (!current.canMoveTo(LeadStatus.CONVERTED)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a QUALIFIED lead can be converted; this lead is " + current);
        }
        CrmContact contact = lead.getContactId() == null ? null : lookups.requireContact(lead.getContactId());

        Long customerId;
        String customerCode;
        if (req != null && req.existingCustomerId() != null) {
            Customer existing = lookups.requireCustomer(req.existingCustomerId());
            customerId = existing.getId();
            customerCode = existing.getCustomerCode();
        } else {
            CustomerDto created = customerService.create(new SaveCustomerRequest(
                    numberService.next("CUS"), lead.getCompanyName(),
                    contact == null ? null : contact.fullName(),
                    contact == null ? null : contact.getPhone(),
                    contact == null ? null : contact.getEmail(),
                    null, null, null, null, null));
            customerId = created.id();
            customerCode = created.customerCode();
        }

        if (contact != null && contact.getCustomerId() == null) {
            contact.setCustomerId(customerId);
            contactRepo.save(contact);
        }
        lead.setConvertedCustomerId(customerId);
        lead.setConvertedAt(Instant.now());
        transition(lead, LeadStatus.CONVERTED, "LEAD_CONVERTED", "customer " + customerCode);
        lead = repo.save(lead);

        OpportunityDto opportunity = opportunityService.createForLead(lead, customerId,
                req == null ? null : req.opportunityDescription());
        activityRepo.attachLeadActivitiesToCustomer(lead.getId(), customerId);
        return new ConvertLeadResultDto(toDto(lead), customerId, customerCode, opportunity);
    }

    private void transition(CrmLead lead, LeadStatus target, String action, String reason) {
        LeadStatus current = LeadStatus.valueOf(lead.getStatus());
        if (!current.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move a lead from " + current + " to " + target);
        }
        lead.setStatus(target.name());
        auditService.record(action, "CRM_LEAD", String.valueOf(lead.getId()), current.name(), target.name(), reason);
    }

    private void apply(CrmLead lead, SaveLeadRequest req) {
        lead.setCompanyName(req.companyName());
        lead.setContactId(req.contactId());
        lead.setSource(req.source());
        lead.setEstimatedValue(req.estimatedValue());
        lead.setExpectedCloseDate(req.expectedCloseDate());
        lead.setNotes(req.notes());
    }

    private CrmLead require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Lead", id));
    }

    private LeadDto toDto(CrmLead l) {
        LeadStatus status = LeadStatus.valueOf(l.getStatus());
        List<String> actions = new ArrayList<>();
        if (status.isOpen()) actions.add("EDIT");
        for (LeadStatus next : List.of(LeadStatus.CONTACTED, LeadStatus.QUALIFIED, LeadStatus.CONVERTED, LeadStatus.UNQUALIFIED)) {
            if (status.canMoveTo(next)) {
                actions.add(switch (next) {
                    case CONTACTED -> "CONTACT";
                    case QUALIFIED -> "QUALIFY";
                    case CONVERTED -> "CONVERT";
                    default -> "DISQUALIFY";
                });
            }
        }
        return new LeadDto(l.getId(), l.getLeadNumber(), l.getCompanyName(), l.getContactId(),
                lookups.contactName(l.getContactId()), l.getSource(), l.getStatus(), l.getOwnerId(),
                lookups.userName(l.getOwnerId()), l.getEstimatedValue(), l.getExpectedCloseDate(),
                l.getConvertedCustomerId(), l.getConvertedAt(), l.getDisqualifyReason(), l.getNotes(), l.getVersion(), actions);
    }
}
