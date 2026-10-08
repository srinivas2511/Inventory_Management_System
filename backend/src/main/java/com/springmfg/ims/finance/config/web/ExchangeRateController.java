package com.springmfg.ims.finance.config.web;

import com.springmfg.ims.finance.config.dto.ExchangeRateDto;
import com.springmfg.ims.finance.config.dto.ExchangeRateRequest;
import com.springmfg.ims.finance.config.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('GL_VIEW','EXCHANGE_RATE_MANAGE')")
    public List<ExchangeRateDto> list(@RequestParam(required = false) Long periodId) {
        return service.list(periodId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_MANAGE')")
    public ExchangeRateDto save(@Valid @RequestBody ExchangeRateRequest request) {
        return service.save(request);
    }
}
