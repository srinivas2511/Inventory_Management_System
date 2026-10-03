package com.springmfg.ims.crm.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.crm.domain.CrmContact;
import com.springmfg.ims.crm.dto.ContactDto;
import com.springmfg.ims.crm.dto.SaveContactRequest;
import com.springmfg.ims.crm.repository.CrmContactRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class CrmContactService {

    private final CrmContactRepository repo;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CrmContactService(CrmContactRepository repo, CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ContactDto> search(Long customerId, String q, Boolean active, Pageable pageable) {
        Specification<CrmContact> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("firstName")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("lastName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("email"), "")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ContactDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public ContactDto create(SaveContactRequest req) {
        if (req.customerId() != null) lookups.requireCustomer(req.customerId());
        CrmContact c = new CrmContact();
        apply(c, req);
        c = repo.save(c);
        auditService.record("CONTACT_CREATE", "CRM_CONTACT", String.valueOf(c.getId()), null, c.fullName(), null);
        return toDto(c);
    }

    @Transactional
    public ContactDto update(Long id, SaveContactRequest req) {
        CrmContact c = require(id);
        CrmLookups.checkVersion(req.version(), c.getVersion(), "Contact");
        if (req.customerId() != null) lookups.requireCustomer(req.customerId());
        String old = c.fullName();
        apply(c, req);
        c = repo.save(c);
        auditService.record("CONTACT_UPDATE", "CRM_CONTACT", String.valueOf(id), old, c.fullName(), null);
        return toDto(c);
    }

    @Transactional
    public void deactivate(Long id) {
        CrmContact c = require(id);
        if (!c.isActive()) return;
        c.setActive(false);
        repo.save(c);
        auditService.record("CONTACT_DEACTIVATE", "CRM_CONTACT", String.valueOf(id), "ACTIVE", "INACTIVE", null);
    }

    private CrmContact require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Contact", id));
    }

    private void apply(CrmContact c, SaveContactRequest req) {
        c.setCustomerId(req.customerId());
        c.setFirstName(req.firstName());
        c.setLastName(req.lastName());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setDesignation(req.designation());
        c.setDepartment(req.department());
    }

    private ContactDto toDto(CrmContact c) {
        return new ContactDto(c.getId(), c.getCustomerId(), lookups.customerName(c.getCustomerId()),
                c.getFirstName(), c.getLastName(), c.fullName(), c.getEmail(), c.getPhone(), c.getDesignation(),
                c.getDepartment(), c.isActive(), c.getVersion());
    }
}
