package com.mfg.ims.finance.dashboard;

import com.mfg.ims.finance.ap.dto.ApAgeingRow;
import com.mfg.ims.finance.ap.service.ApService;
import com.mfg.ims.finance.ar.dto.ArAgeingRow;
import com.mfg.ims.finance.ar.service.ArService;
import com.mfg.ims.finance.gl.service.FiscalPeriodService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/dashboard")
public class FinanceDashboardController {

    private final ApService apService;
    private final ArService arService;
    private final FiscalPeriodService periodService;

    public FinanceDashboardController(ApService apService,
                                      ArService arService,
                                      FiscalPeriodService periodService) {
        this.apService = apService;
        this.arService = arService;
        this.periodService = periodService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public FinanceDashboardDto getDashboard() {
        String openPeriodName = periodService.findOpenPeriodForDate(LocalDate.now())
                .map(p -> p.periodName()).orElse("No open period");

        List<ApAgeingRow> apAgeing = apService.getAgeing();
        BigDecimal totalAp = apAgeing.stream().map(ApAgeingRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal apOverdue = apAgeing.stream()
                .map(r -> r.overdue30().add(r.overdue60()).add(r.overdue90()).add(r.overdue90Plus()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ArAgeingRow> arAgeing = arService.getAgeing();
        BigDecimal totalAr = arAgeing.stream().map(ArAgeingRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal arOverdue = arAgeing.stream()
                .map(r -> r.overdue30().add(r.overdue60()).add(r.overdue90()).add(r.overdue90Plus()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceDashboardDto(openPeriodName, totalAp, totalAr,
                apOverdue, arOverdue, List.of());
    }
}
