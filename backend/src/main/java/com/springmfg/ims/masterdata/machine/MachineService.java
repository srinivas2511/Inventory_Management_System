package com.springmfg.ims.masterdata.machine;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;

@Service
public class MachineService {

    private final MachineRepository repo;
    private final AuditService auditService;
    private final CostCentreRepository costCentres;

    public MachineService(MachineRepository repo, AuditService auditService, CostCentreRepository costCentres) {
        this.repo = repo;
        this.auditService = auditService;
        this.costCentres = costCentres;
    }

    @Transactional(readOnly = true)
    public List<MachineDto> findAll() {
        return repo.findAll().stream().map(MachineDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MachineDto> findActive() {
        return repo.findAllByActiveTrue().stream().map(MachineDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MachineDto findById(Long id) {
        return repo.findById(id).map(MachineDto::from)
                .orElseThrow(() -> new NotFoundException("Machine not found: " + id));
    }

    @Transactional
    public MachineDto save(SaveMachineRequest req) {
        Machine machine = repo.findByMachineCode(req.code()).orElse(null);
        if (machine == null) {
            if (repo.existsByMachineCode(req.code())) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Machine code already exists: " + req.code());
            }
            machine = new Machine();
            machine.setMachineCode(req.code());
        }
        machine.setName(req.name());
        machine.setMachineType(req.machineType());
        machine.setProductionLine(req.productionLine());
        if (req.status() != null) machine.setStatus(req.status());
        if (req.costCentreId() != null) {
            if (!costCentres.existsById(req.costCentreId())) {
                throw new NotFoundException("Cost centre not found: " + req.costCentreId());
            }
            machine.setCostCentreId(req.costCentreId());
        }
        machine = repo.save(machine);
        auditService.record("MACHINE_SAVE", "Machine", String.valueOf(machine.getId()), null, req.code(), null);
        return MachineDto.from(machine);
    }

    @Transactional
    public MachineDto updateStatus(Long id, String status) {
        Machine machine = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Machine not found: " + id));
        String old = machine.getStatus();
        machine.setStatus(status);
        machine = repo.save(machine);
        auditService.record("MACHINE_STATUS", "Machine", String.valueOf(id), old, status, null);
        return MachineDto.from(machine);
    }

    @Transactional
    public MachineDto deactivate(Long id) {
        Machine machine = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Machine not found: " + id));
        machine.setActive(false);
        machine = repo.save(machine);
        auditService.record("MACHINE_DEACTIVATE", "Machine", String.valueOf(id), "true", "false", null);
        return MachineDto.from(machine);
    }
}
