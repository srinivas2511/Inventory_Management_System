package com.springmfg.ims.production.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.production.domain.ProductionOperation;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.repository.ProductionOperationRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;

/**
 * Gives production operations an operator (DESIGN 4.6): operators and supervisors only see the operations assigned to
 * them, so nothing can be done on the shop floor until somebody assigns the work. Assigning is idempotent, audited
 * with old and new operator, and refused for finished operations and for orders that are not running.
 */
@Service
public class OperationAssignmentService {

    private static final Set<String> ASSIGNABLE_ORDER_STATUSES = Set.of("RELEASED", "IN_PROGRESS");
    private static final Set<String> OPEN_OPERATION_STATUSES = Set.of("PENDING", "IN_PROGRESS", "PAUSED");

    private final ProductionOperationRepository opRepo;
    private final ProductionOrderRepository orderRepo;
    private final OperatorDirectory operatorDirectory;
    private final OperationExecutionService executionService;
    private final AuditService auditService;

    private final com.springmfg.ims.enterprise.scope.PlantScope plantScope;


    public OperationAssignmentService(ProductionOperationRepository opRepo,
                                      ProductionOrderRepository orderRepo,
                                      OperatorDirectory operatorDirectory,
                                      OperationExecutionService executionService,
                                      AuditService auditService,

            com.springmfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.opRepo = opRepo;
        this.orderRepo = orderRepo;
        this.operatorDirectory = operatorDirectory;
        this.executionService = executionService;
        this.auditService = auditService;
    }

    /** Assigns one operation; a null operator unassigns it. */
    @Transactional
    public ProductionOperationDto assign(Long operationId, Long operatorId, String reason) {
        ProductionOperation op = opRepo.findById(operationId)
                .orElseThrow(() -> new NotFoundException("Operation not found: " + operationId));
        ProductionOrder order = requireAssignableOrder(op.getProductionOrderId());
        requireOperator(operatorId);
        change(op, order, operatorId, reason);
        return executionService.toDto(op);
    }

    /** Assigns every unfinished operation of an order to one operator (or unassigns them all). */
    @Transactional
    public List<ProductionOperationDto> assignAll(Long orderId, Long operatorId, String reason) {
        ProductionOrder order = requireAssignableOrder(orderId);
        requireOperator(operatorId);
        List<ProductionOperationDto> result = new ArrayList<>();
        for (ProductionOperation op : opRepo.findByProductionOrderIdOrderBySequenceNo(orderId)) {
            if (OPEN_OPERATION_STATUSES.contains(op.getStatus())) {
                change(op, order, operatorId, reason);
            }
            result.add(executionService.toDto(op));
        }
        return result;
    }

    private void change(ProductionOperation op, ProductionOrder order, Long operatorId, String reason) {
        if (!OPEN_OPERATION_STATUSES.contains(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation " + op.getSequenceNo() + " of " + order.getOrderNumber() + " is "
                            + op.getStatus() + " and can no longer be assigned");
        }
        Long previous = op.getAssignedOperatorId();
        if (java.util.Objects.equals(previous, operatorId)) {
            return;
        }
        op.setAssignedOperatorId(operatorId);
        opRepo.save(op);
        auditService.record("OP_ASSIGN", "ProductionOperation", String.valueOf(op.getId()),
                previous == null ? null : String.valueOf(previous),
                operatorId == null ? null : String.valueOf(operatorId),
                reason == null || reason.isBlank() ? null : reason.trim());
    }

    private ProductionOrder requireAssignableOrder(Long orderId) {
        ProductionOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Production order not found: " + orderId));
        plantScope.assertVisible(order.getPlantId(), "Production order", orderId);
        if (!ASSIGNABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operators can only be assigned while the order is released or in progress; "
                            + order.getOrderNumber() + " is " + order.getStatus());
        }
        return order;
    }

    private void requireOperator(Long operatorId) {
        if (operatorId != null && !operatorDirectory.isAssignable(operatorId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "User " + operatorId + " cannot be assigned: the user must be active, not locked, and hold a role "
                            + "that can execute production work");
        }
    }
}
