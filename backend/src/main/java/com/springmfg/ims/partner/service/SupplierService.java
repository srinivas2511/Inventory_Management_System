package com.springmfg.ims.partner.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.dto.SaveSupplierRequest;
import com.springmfg.ims.partner.dto.SupplierDto;
import com.springmfg.ims.partner.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository repo;
    private final AuditService auditService;

    public SupplierService(SupplierRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierDto> search(String q, String status, Pageable pageable) {
        return repo.search(q, status, pageable).map(SupplierDto::from);
    }

    @Transactional(readOnly = true)
    public SupplierDto findById(Long id) {
        return SupplierDto.from(require(id));
    }

    @Transactional
    public SupplierDto create(SaveSupplierRequest req) {
        if (repo.existsBySupplierCode(req.supplierCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Supplier code already exists: " + req.supplierCode());
        }
        Supplier s = new Supplier();
        apply(s, req);
        repo.save(s);
        auditService.record("CREATE", "SUPPLIER", String.valueOf(s.getId()), null, s.getSupplierCode(), null);
        return SupplierDto.from(s);
    }

    @Transactional
    public SupplierDto update(Long id, SaveSupplierRequest req) {
        Supplier s = require(id);
        String old = s.getName();
        apply(s, req);
        repo.save(s);
        auditService.record("UPDATE", "SUPPLIER", String.valueOf(id), old, s.getName(), null);
        return SupplierDto.from(s);
    }

    @Transactional
    public void deactivate(Long id) {
        Supplier s = require(id);
        if ("INACTIVE".equals(s.getStatus())) return;
        s.setStatus("INACTIVE");
        repo.save(s);
        auditService.record("DEACTIVATE", "SUPPLIER", String.valueOf(id), "ACTIVE", "INACTIVE", null);
    }

    @Transactional
    public void reactivate(Long id) {
        Supplier s = require(id);
        if ("ACTIVE".equals(s.getStatus())) return;
        s.setStatus("ACTIVE");
        repo.save(s);
        auditService.record("REACTIVATE", "SUPPLIER", String.valueOf(id), "INACTIVE", "ACTIVE", null);
    }

    private void apply(Supplier s, SaveSupplierRequest req) {
        s.setSupplierCode(req.supplierCode());
        s.setName(req.name());
        s.setContactPerson(req.contactPerson());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setGstNumber(req.gstNumber());
        s.setPaymentTerms(req.paymentTerms());
        s.setLeadTimeDays(req.leadTimeDays());
    }

    private Supplier require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found: " + id));
    }
}
