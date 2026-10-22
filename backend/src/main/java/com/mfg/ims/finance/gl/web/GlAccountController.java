package com.mfg.ims.finance.gl.web;

import com.mfg.ims.auth.stepup.StepUpService;
import com.mfg.ims.finance.gl.dto.GlAccountDto;
import com.mfg.ims.finance.gl.dto.TrialBalanceRow;
import com.mfg.ims.finance.gl.service.GlAccountService;
import com.mfg.ims.finance.gl.service.JournalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gl")
public class GlAccountController {

    private final GlAccountService glAccountService;
    private final JournalService journalService;
    private final StepUpService stepUp;

    public GlAccountController(GlAccountService glAccountService, JournalService journalService, StepUpService stepUp) {
        this.glAccountService = glAccountService;
        this.journalService = journalService;
        this.stepUp = stepUp;
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public List<GlAccountDto> listAccounts() {
        return glAccountService.findActive();
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public List<TrialBalanceRow> trialBalance(@RequestParam Long periodId) {
        stepUp.requireFresh();
        return journalService.getTrialBalance(periodId);
    }
}
