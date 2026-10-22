package com.mfg.ims.maintenance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.controlling.domain.CostCentre;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.maintenance.domain.MaintenanceRequest;
import com.mfg.ims.maintenance.domain.MaintenanceStatus;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.MaintenanceCostDto;
import com.mfg.ims.maintenance.repository.MaintenanceActivityRepository;
import com.mfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.mfg.ims.maintenance.repository.SparePartIssueLineRepository;

/**
 * Maintenance cost of a request: labour (activity minutes at the machine's cost centre rate) plus the spare parts
 * issued to it. The labour is fixed when the request completes and is then posted to the machine's cost centre as
 * Dr Repairs and Maintenance Expense / Cr Maintenance Labour Absorbed. Spare parts reach the GL through the inventory
 * bridge, charged to the same cost centre.
 */
@Service
public class MaintenanceCostService {

    public static final String MAINTENANCE_EXPENSE = "5301";
    public static final String LABOUR_ABSORBED = "5302";

    private final MaintenanceRequestRepository requestRepo;
    private final MaintenanceActivityRepository activityRepo;
    private final SparePartIssueLineRepository issueRepo;
    private final MaintenanceCostCentres costCentres;
    private final JournalService journalService;
    private final GlAccountResolver glAccounts;
    private final SystemSettingService settings;
    private final AuditService auditService;

    public MaintenanceCostService(MaintenanceRequestRepository requestRepo,
                                  MaintenanceActivityRepository activityRepo,
                                  SparePartIssueLineRepository issueRepo,
                                  MaintenanceCostCentres costCentres,
                                  JournalService journalService,
                                  GlAccountResolver glAccounts,
                                  SystemSettingService settings,
                                  AuditService auditService) {
        this.requestRepo = requestRepo;
        this.activityRepo = activityRepo;
        this.issueRepo = issueRepo;
        this.costCentres = costCentres;
        this.journalService = journalService;
        this.glAccounts = glAccounts;
        this.settings = settings;
        this.auditService = auditService;
    }

    /** Fixes the labour hours, cost and cost centre on the request; called when it completes, in that transaction. */
    @Transactional
    public void snapshotLabour(MaintenanceRequest request) {
        long minutes = activityRepo.totalMinutes(request.getId());
        Optional<CostCentre> centre = costCentres.forMachine(request.getMachineId());
        request.setCostCentreId(centre.map(CostCentre::getId).orElse(null));
        request.setLabourHours(MaintenanceCostCalculator.labourHours(minutes));
        request.setLabourCost(MaintenanceCostCalculator.labourCost(minutes,
                centre.map(CostCentre::getLabourRatePerHour).orElse(BigDecimal.ZERO)));
    }

    @Transactional(readOnly = true)
    public MaintenanceCostDto cost(Long requestId) {
        MaintenanceRequest r = requestRepo.findById(requestId)
                .orElseThrow(() -> new NotFoundException("MaintenanceRequest", requestId));
        long minutes = activityRepo.totalMinutes(requestId);
        BigDecimal spare = issueRepo.totalCost(requestId);
        boolean fixed = MaintenanceStatus.COMPLETED.name().equals(r.getStatus()) && r.getLabourCost() != null;
        Optional<CostCentre> centre = fixed && r.getCostCentreId() != null
                ? costCentres.byId(r.getCostCentreId())
                : costCentres.forMachine(r.getMachineId());
        BigDecimal rate = centre.map(CostCentre::getLabourRatePerHour).orElse(BigDecimal.ZERO);
        BigDecimal labourCost = fixed ? r.getLabourCost() : MaintenanceCostCalculator.labourCost(minutes, rate);
        BigDecimal labourHours = fixed ? r.getLabourHours() : MaintenanceCostCalculator.labourHours(minutes);
        Long centreId = fixed ? r.getCostCentreId() : centre.map(CostCentre::getId).orElse(null);
        return new MaintenanceCostDto(r.getId(), r.getRequestNumber(), r.getMachineId(), r.getStatus(), centreId,
                minutes, labourHours, rate, labourCost, spare, labourCost.add(spare), fixed,
                r.getLabourJournalHeaderId(), r.getCostPostedAt());
    }

    /**
     * Posts the labour cost of a completed request. Idempotent: a request already posted is left alone, and a request
     * with no labour cost is marked posted without a journal. Runs in its own transaction because it is also the
     * after-commit step of completing a request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaintenanceCostDto postLabourCost(Long requestId) {
        MaintenanceRequest r = requestRepo.findById(requestId)
                .orElseThrow(() -> new NotFoundException("MaintenanceRequest", requestId));
        if (!MaintenanceStatus.COMPLETED.name().equals(r.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a completed maintenance request has a cost to post; " + r.getRequestNumber() + " is "
                            + r.getStatus());
        }
        if (r.getCostPostedAt() == null) {
            BigDecimal labour = r.getLabourCost() == null ? BigDecimal.ZERO : r.getLabourCost();
            if (labour.signum() > 0) {
                if (r.getCostCentreId() == null) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "Machine " + r.getMachineId() + " has no cost centre to charge the labour to");
                }
                JournalHeaderDto journal = journalService.postSourceJournal(new AutoJournalRequest(
                        "AUTO", "MAINTENANCE", "MAINTENANCE_REQUEST", r.getId(), postingDate(r),
                        "Maintenance labour " + r.getRequestNumber(), currentUserIdOrNull(),
                        List.of(new JournalLineRequest(glAccounts.idOf(MAINTENANCE_EXPENSE), labour, BigDecimal.ZERO,
                                        "Labour " + r.getRequestNumber(), r.getCostCentreId(), null),
                                new JournalLineRequest(glAccounts.idOf(LABOUR_ABSORBED), BigDecimal.ZERO, labour,
                                        "Labour " + r.getRequestNumber(), r.getCostCentreId(), null))));
                r.setLabourJournalHeaderId(journal.id());
            }
            r.setCostPostedAt(Instant.now());
            requestRepo.save(r);
            auditService.record("MAINTENANCE_COST_POST", "MaintenanceRequest", String.valueOf(r.getId()), null,
                    "labour " + labour.toPlainString(), null);
        }
        return cost(requestId);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceCostDto> unposted() {
        return requestRepo.findCompletedWithUnpostedCost().stream().map(r -> cost(r.getId())).toList();
    }

    private LocalDate postingDate(MaintenanceRequest r) {
        Instant when = r.getCompletedAt() != null ? r.getCompletedAt() : Instant.now();
        return when.atZone(ZoneId.of(settings.get("hrm.timezone", "Asia/Kolkata").trim())).toLocalDate();
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }
}
