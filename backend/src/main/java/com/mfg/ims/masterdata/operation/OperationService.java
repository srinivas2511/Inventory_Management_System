package com.mfg.ims.masterdata.operation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class OperationService {

    private final OperationRepository repo;
    private final AuditService auditService;

    public OperationService(OperationRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OperationDto> findAll() {
        return repo.findAll().stream().map(OperationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OperationDto> findActive() {
        return repo.findAllByActiveTrue().stream().map(OperationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OperationDto findById(Long id) {
        return repo.findById(id).map(OperationDto::from)
                .orElseThrow(() -> new NotFoundException("Operation not found: " + id));
    }

    @Transactional
    public OperationDto save(SaveOperationRequest req) {
        Operation op = repo.findByOperationCode(req.code()).orElse(null);
        if (op == null) {
            if (repo.existsByOperationCode(req.code())) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Operation code already exists: " + req.code());
            }
            op = new Operation();
            op.setOperationCode(req.code());
        }
        op.setName(req.name());
        op.setDescription(req.description());
        op.setDefaultMachineType(req.defaultMachineType());
        op.setStandardTimeMin(req.standardTimeMin());
        op.setSetupTimeMin(req.setupTimeMin());
        op.setInspection(req.isInspection());
        op = repo.save(op);
        auditService.record("OPERATION_SAVE", "Operation", String.valueOf(op.getId()), null, req.code(), null);
        return OperationDto.from(op);
    }

    @Transactional
    public OperationDto deactivate(Long id) {
        Operation op = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Operation not found: " + id));
        op.setActive(false);
        op = repo.save(op);
        auditService.record("OPERATION_DEACTIVATE", "Operation", String.valueOf(id), "true", "false", null);
        return OperationDto.from(op);
    }
}
