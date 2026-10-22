package com.mfg.ims.finance.gl.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.dto.FiscalPeriodDto;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FiscalPeriodService {

    private final FiscalPeriodRepository periodRepo;
    private final AuditService auditService;

    public FiscalPeriodService(FiscalPeriodRepository periodRepo, AuditService auditService) {
        this.periodRepo = periodRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FiscalPeriodDto> findAll() {
        return periodRepo.findAllByOrderByFiscalYearAscPeriodNumberAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FiscalPeriodDto> findOpenPeriodForDate(LocalDate date) {
        return periodRepo.findOpenPeriodForDate(date).map(this::toDto);
    }

    public FiscalPeriodDto initiatePeriodClose(Long id) {
        FiscalPeriod period = findPeriod(id);
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not OPEN");
        }
        period.setStatus("CLOSING");
        auditService.record("PERIOD_CLOSE_INITIATED", "FISCAL_PERIOD", String.valueOf(id),
                "OPEN", "CLOSING", null);
        return toDto(periodRepo.save(period));
    }

    public FiscalPeriodDto finalizePeriodClose(Long id, String reason) {
        String why = requireReason(reason);
        FiscalPeriod period = findPeriod(id);
        if (!"CLOSING".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not in CLOSING state");
        }
        period.setStatus("CLOSED");
        period.setClosedBy(currentUserId());
        period.setClosedAt(Instant.now());
        auditService.record("PERIOD_CLOSED", "FISCAL_PERIOD", String.valueOf(id),
                "CLOSING", "CLOSED", why);
        return toDto(periodRepo.save(period));
    }

    public FiscalPeriodDto reopenPeriod(Long id, String reason) {
        String why = requireReason(reason);
        FiscalPeriod period = findPeriod(id);
        if (!"CLOSED".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not CLOSED");
        }
        period.setStatus("OPEN");
        period.setClosedBy(null);
        period.setClosedAt(null);
        auditService.record("PERIOD_REOPENED", "FISCAL_PERIOD", String.valueOf(id),
                "CLOSED", "OPEN", why);
        return toDto(periodRepo.save(period));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required to close or reopen a period");
        }
        return reason.trim();
    }

    private FiscalPeriod findPeriod(Long id) {
        return periodRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", id));
    }

    private FiscalPeriodDto toDto(FiscalPeriod p) {
        return new FiscalPeriodDto(p.getId(), p.getPeriodName(), p.getFiscalYear(),
                p.getPeriodNumber(), p.getStartDate(), p.getEndDate(), p.getStatus());
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
