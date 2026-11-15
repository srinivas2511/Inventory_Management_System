package com.mfg.ims.partner.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.partner.domain.Supplier;
import com.mfg.ims.partner.dto.SaveSupplierRequest;
import com.mfg.ims.partner.dto.SupplierDto;
import com.mfg.ims.partner.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository repo;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    public SupplierService(SupplierRepository repo, AuditService auditService,
            NamedParameterJdbcTemplate jdbc) {
        this.repo = repo;
        this.auditService = auditService;
        this.jdbc = jdbc;
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
        String code = (req.supplierCode() == null || req.supplierCode().isBlank())
                ? generateCode()
                : req.supplierCode().trim();
        if (repo.existsBySupplierCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Supplier code already exists: " + code);
        }
        Supplier s = new Supplier();
        s.setSupplierCode(code);
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

    private String generateCode() {
        Long next = jdbc.queryForObject(
                "SELECT nextval('ims.supplier_code_seq')", java.util.Map.of(), Long.class);
        return String.format("SUP-%04d", next);
    }

    private void apply(Supplier s, SaveSupplierRequest req) {
        s.setName(req.name());
        s.setContactPerson(req.contactPerson());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setAddressLine1(req.addressLine1());
        s.setAddressLine2(req.addressLine2());
        s.setCity(req.city());
        s.setStateName(req.stateName());
        s.setPinCode(req.pinCode());
        s.setCountry(req.country() == null || req.country().isBlank() ? "India" : req.country());
        s.setPan(req.pan() == null || req.pan().isBlank() ? null : req.pan().trim().toUpperCase());
        s.setGstNumber(req.gstNumber() == null || req.gstNumber().isBlank() ? null : req.gstNumber().trim());
        s.setCurrencyCode(req.currencyCode() == null || req.currencyCode().isBlank() ? "INR" : req.currencyCode());
        s.setPaymentTerms(req.paymentTerms());
        s.setPaymentTermsId(req.paymentTermsId());
        s.setLeadTimeDays(req.leadTimeDays());
        s.setNotes(req.notes());
        s.setVendorTypeId(req.vendorTypeId());
    }

    private Supplier require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found: " + id));
    }
}
