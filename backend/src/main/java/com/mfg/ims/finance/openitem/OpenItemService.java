package com.mfg.ims.finance.openitem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OpenItemService {

    private final OpenItemRepository repo;

    public OpenItemService(OpenItemRepository repo) {
        this.repo = repo;
    }

    public List<OpenItemDto> listOpen(String partyType, Long partyId) {
        return repo.findByPartyTypeAndPartyIdAndBalanceGreaterThanOrderByDueDateAsc(
                partyType, partyId, BigDecimal.ZERO)
                .stream().map(this::toDto).toList();
    }

    public List<OpenItemDto> ageingReport() {
        return repo.findByCompanyIdAndBalanceGreaterThanOrderByDueDateAsc(1L, BigDecimal.ZERO)
                .stream().map(this::toDto).toList();
    }

    private OpenItemDto toDto(OpenItem oi) {
        String bucket = ageBucket(oi.getDueDate());
        return new OpenItemDto(oi.getId(), oi.getItemType(), oi.getPartyType(), oi.getPartyId(),
                oi.getSourceType(), oi.getSourceId(), oi.getDocNo(), oi.getDocDate(),
                oi.getDueDate(), oi.getCurrency(), oi.getAmount(), oi.getAmountInr(),
                oi.getBalance(), oi.getBalanceInr(), bucket);
    }

    private String ageBucket(LocalDate dueDate) {
        if (dueDate == null) return "CURRENT";
        LocalDate today = LocalDate.now();
        if (!today.isAfter(dueDate)) return "CURRENT";
        long days = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
        if (days <= 30) return "OVERDUE_30";
        if (days <= 60) return "OVERDUE_60";
        if (days <= 90) return "OVERDUE_90";
        return "OVERDUE_90PLUS";
    }
}
