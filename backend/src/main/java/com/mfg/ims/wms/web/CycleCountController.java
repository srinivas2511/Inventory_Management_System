package com.mfg.ims.wms.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.wms.domain.WmsEnums.CycleCountStatus;
import com.mfg.ims.wms.dto.WmsDtos.CancelRequest;
import com.mfg.ims.wms.dto.WmsDtos.CreateCycleCountRequest;
import com.mfg.ims.wms.dto.WmsDtos.CycleCountDto;
import com.mfg.ims.wms.dto.WmsDtos.RecordCountsRequest;
import com.mfg.ims.wms.service.CycleCountService;

@RestController
@RequestMapping("/api/wms/cycle-counts")
public class CycleCountController {

    private final CycleCountService service;

    public CycleCountController(CycleCountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public Page<CycleCountDto> list(@RequestParam(required = false) CycleCountStatus status,
                                    @RequestParam(required = false) Long warehouseId,
                                    @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, warehouseId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public CycleCountDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto create(@Valid @RequestBody CreateCycleCountRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto recordCounts(@PathVariable Long id, @Valid @RequestBody RecordCountsRequest req) {
        return service.recordCounts(id, req);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto review(@PathVariable Long id) {
        return service.review(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CYCLE_COUNT_MANAGE')")
    public CycleCountDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }
}
