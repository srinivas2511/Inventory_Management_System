package com.springmfg.ims.wms.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.wms.dto.WmsDtos.PutAwayRuleDto;
import com.springmfg.ims.wms.dto.WmsDtos.SavePutAwayRuleRequest;
import com.springmfg.ims.wms.service.BinService;

@RestController
@RequestMapping("/api/wms/putaway-rules")
public class PutAwayRuleController {

    private final BinService service;

    public PutAwayRuleController(BinService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public List<PutAwayRuleDto> list(@RequestParam Long warehouseId) {
        return service.listRules(warehouseId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public PutAwayRuleDto save(@Valid @RequestBody SavePutAwayRuleRequest req) {
        return service.saveRule(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.deleteRule(id);
    }
}
