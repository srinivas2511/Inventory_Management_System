package com.springmfg.ims.hrm.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.hrm.dto.PayrollDtos.InitiatePayrollRequest;
import com.springmfg.ims.hrm.dto.PayrollDtos.MarkPaidRequest;
import com.springmfg.ims.hrm.dto.PayrollDtos.PayrollRunDto;
import com.springmfg.ims.hrm.dto.PayrollDtos.PayrollSlipDto;
import com.springmfg.ims.hrm.service.PayrollService;

import jakarta.validation.Valid;

/** Everything under payroll needs PAYROLL_RUN: pay data is as sensitive to read as to change. */
@RestController
@RequestMapping("/api/hrm/payroll")
public class PayrollController {

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public List<PayrollRunDto> runs() {
        return service.list();
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollRunDto run(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/runs/{id}/slips")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public List<PayrollSlipDto> slips(@PathVariable Long id) {
        return service.slips(id);
    }

    @GetMapping("/slips/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollSlipDto slip(@PathVariable Long id) {
        return service.slip(id);
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollRunDto initiate(@Valid @RequestBody InitiatePayrollRequest req) {
        return service.initiate(req);
    }

    @PostMapping("/runs/{id}/calculate")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollRunDto calculate(@PathVariable Long id) {
        return service.calculate(id);
    }

    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollRunDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/runs/{id}/pay")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public PayrollRunDto pay(@PathVariable Long id, @RequestBody(required = false) MarkPaidRequest req) {
        return service.markPaid(id, req);
    }
}
