package com.mfg.ims.procurement.vendortype;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class VendorTypeService {

    private final VendorTypeRepository repo;
    private final AuditService auditService;

    public VendorTypeService(VendorTypeRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<VendorTypeDto> findAll() {
        return repo.findAllByOrderByCode().stream().map(VendorTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VendorTypeDto> findActive() {
        return repo.findAllByActiveTrueOrderByCode().stream().map(VendorTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public VendorTypeDto findById(Long id) {
        return VendorTypeDto.from(require(id));
    }

    @Transactional
    public VendorTypeDto create(SaveVendorTypeRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Vendor type code already exists: " + req.code());
        }
        VendorType vt = new VendorType();
        apply(vt, req);
        repo.save(vt);
        auditService.record("CREATE", "VENDOR_TYPE", String.valueOf(vt.getId()), null, vt.getCode(), null);
        return VendorTypeDto.from(vt);
    }

    @Transactional
    public VendorTypeDto update(Long id, SaveVendorTypeRequest req) {
        VendorType vt = require(id);
        String old = vt.getCode();
        apply(vt, req);
        repo.save(vt);
        auditService.record("UPDATE", "VENDOR_TYPE", String.valueOf(id), old, vt.getDescription(), null);
        return VendorTypeDto.from(vt);
    }

    private void apply(VendorType vt, SaveVendorTypeRequest req) {
        vt.setCode(req.code());
        vt.setDescription(req.description());
        vt.setActive(req.active());
    }

    private VendorType require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Vendor type not found: " + id));
    }
}
