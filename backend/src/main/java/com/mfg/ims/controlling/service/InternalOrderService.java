package com.mfg.ims.controlling.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.controlling.domain.InternalOrder;
import com.mfg.ims.controlling.dto.*;
import com.mfg.ims.controlling.repository.InternalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class InternalOrderService {

    private final InternalOrderRepository repo;
    private final AuditService auditService;

    public InternalOrderService(InternalOrderRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<InternalOrderDto> findAll() {
        return repo.findAllByOrderByOrderNumberAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public InternalOrderDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public InternalOrderDto create(CreateInternalOrderRequest req) {
        if (repo.existsByOrderNumber(req.orderNumber())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Internal order number already exists: " + req.orderNumber());
        }
        InternalOrder order = new InternalOrder();
        order.setOrderNumber(req.orderNumber());
        order.setDescription(req.description());
        order.setOrderType(req.orderType());
        order.setResponsibleCostCentreId(req.responsibleCostCentreId());
        order.setBudgetAmount(req.budgetAmount() != null ? req.budgetAmount() : BigDecimal.ZERO);
        order.setStartDate(req.startDate());
        order.setEndDate(req.endDate());
        order.setStatus("OPEN");
        order = repo.save(order);
        auditService.record("CREATED", "INTERNAL_ORDER", String.valueOf(order.getId()), null, order.getOrderNumber(), null);
        return toDto(order);
    }

    @Transactional
    public InternalOrderDto update(Long id, CreateInternalOrderRequest req) {
        InternalOrder order = require(id);
        if (!"OPEN".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only OPEN internal orders can be updated");
        }
        order.setDescription(req.description());
        order.setOrderType(req.orderType());
        order.setResponsibleCostCentreId(req.responsibleCostCentreId());
        order.setBudgetAmount(req.budgetAmount() != null ? req.budgetAmount() : BigDecimal.ZERO);
        order.setStartDate(req.startDate());
        order.setEndDate(req.endDate());
        return toDto(repo.save(order));
    }

    @Transactional(readOnly = true)
    public List<InternalOrderCostRow> getCosts(Long id) {
        require(id);
        return repo.collectCosts(id).stream().map(row -> {
            Long glAccountId = toLong(row[0]);
            String accountCode = (String) row[1];
            String accountName = (String) row[2];
            BigDecimal dr = toBd(row[3]);
            BigDecimal cr = toBd(row[4]);
            return new InternalOrderCostRow(glAccountId, accountCode, accountName, dr, cr, dr.subtract(cr));
        }).toList();
    }

    @Transactional
    public InternalOrderDto settle(Long id, SettleInternalOrderRequest req) {
        InternalOrder order = require(id);
        if (!"OPEN".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only OPEN internal orders can be settled");
        }
        BigDecimal totalCost = repo.totalNetCost(id);
        order.setStatus("SETTLED");
        order.setSettledAmount(totalCost != null ? totalCost : BigDecimal.ZERO);
        order.setSettledAt(Instant.now());
        order.setSettlementCostCentreId(req.settlementCostCentreId());
        order.setActualAmount(order.getSettledAmount());
        auditService.record("SETTLED", "INTERNAL_ORDER", String.valueOf(order.getId()),
                order.getOrderNumber(), req.settlementCostCentreId().toString(), req.reason());
        return toDto(repo.save(order));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InternalOrder require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("InternalOrder", id));
    }

    private InternalOrderDto toDto(InternalOrder o) {
        return new InternalOrderDto(o.getId(), o.getOrderNumber(), o.getDescription(), o.getOrderType(),
                o.getResponsibleCostCentreId(), o.getBudgetAmount(), o.getActualAmount(),
                o.getStatus(), o.getSettlementCostCentreId(), o.getSettledAmount(),
                o.getSettledAt(), o.getSettledBy(), o.getSettlementJournalId(),
                o.getStartDate(), o.getEndDate());
    }

    private static Long toLong(Object v) {
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private static BigDecimal toBd(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
