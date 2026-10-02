package com.springmfg.ims.engineering.routing;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.masterdata.machine.MachineRepository;
import com.springmfg.ims.masterdata.operation.OperationRepository;

@Service
public class RoutingService {

    private final ProductRoutingRepository routingRepo;
    private final OperationRepository operationRepo;
    private final MachineRepository machineRepo;
    private final AuditService auditService;

    public RoutingService(ProductRoutingRepository routingRepo,
                          OperationRepository operationRepo,
                          MachineRepository machineRepo,
                          AuditService auditService) {
        this.routingRepo = routingRepo;
        this.operationRepo = operationRepo;
        this.machineRepo = machineRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProductRoutingDto> getForProduct(Long productId) {
        return routingRepo.findByProductIdOrderBySequenceNo(productId)
                .stream()
                .map(r -> {
                    String opName = operationRepo.findById(r.getOperationId())
                            .map(o -> o.getName()).orElse(null);
                    String machineName = r.getMachineId() != null
                            ? machineRepo.findById(r.getMachineId()).map(m -> m.getName()).orElse(null)
                            : null;
                    return ProductRoutingDto.from(r, opName, machineName);
                })
                .toList();
    }

    @Transactional
    public List<ProductRoutingDto> saveSteps(Long productId, List<SaveRoutingStepRequest> steps) {
        // Deactivate existing active steps
        List<ProductRouting> existing = routingRepo.findByProductIdAndActiveTrue(productId);
        existing.forEach(r -> r.setActive(false));
        routingRepo.saveAll(existing);

        // Create new steps
        List<ProductRouting> newSteps = steps.stream().map(req -> {
            if (!operationRepo.existsById(req.operationId())) {
                throw new NotFoundException("Operation not found: " + req.operationId());
            }
            ProductRouting r = new ProductRouting();
            r.setProductId(productId);
            r.setSequenceNo(req.sequenceNo());
            r.setOperationId(req.operationId());
            r.setMachineId(req.machineId());
            r.setStandardTimeMin(req.standardTimeMin());
            r.setSetupTimeMin(req.setupTimeMin());
            r.setInspectionPoint(req.inspectionPoint());
            return r;
        }).toList();

        routingRepo.saveAll(newSteps);
        auditService.record("ROUTING_SAVE", "ProductRouting", String.valueOf(productId),
                String.valueOf(existing.size()) + " steps", String.valueOf(newSteps.size()) + " steps", null);

        return getForProduct(productId);
    }
}
