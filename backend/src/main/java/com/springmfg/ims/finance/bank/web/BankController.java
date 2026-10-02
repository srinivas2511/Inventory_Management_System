package com.springmfg.ims.finance.bank.web;

import com.springmfg.ims.finance.bank.dto.BankStatementDto;
import com.springmfg.ims.finance.bank.dto.BankStatementLineDto;
import com.springmfg.ims.finance.bank.service.BankReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final BankReconciliationService bankService;

    public BankController(BankReconciliationService bankService) {
        this.bankService = bankService;
    }

    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('BANK_RECONCILE')")
    public Page<BankStatementDto> listStatements(Pageable pageable) {
        return bankService.findAll(pageable);
    }

    @GetMapping("/statements/{id}/lines")
    @PreAuthorize("hasAuthority('BANK_RECONCILE')")
    public List<BankStatementLineDto> listLines(@PathVariable Long id) {
        return bankService.findLines(id);
    }

    @PostMapping("/statements/{id}/auto-match")
    @PreAuthorize("hasAuthority('BANK_RECONCILE')")
    public BankStatementDto autoMatch(@PathVariable Long id) {
        return bankService.autoMatch(id);
    }

    @PostMapping("/statements/{id}/match-line")
    @PreAuthorize("hasAuthority('BANK_RECONCILE')")
    public BankStatementDto manualMatch(@PathVariable Long id,
                                        @RequestParam Long lineId,
                                        @RequestParam Long journalLineId) {
        return bankService.manualMatch(id, lineId, journalLineId);
    }

    @PostMapping("/statements/{id}/reconcile")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('BANK_RECONCILE')")
    public BankStatementDto reconcile(@PathVariable Long id) {
        return bankService.reconcile(id);
    }
}
