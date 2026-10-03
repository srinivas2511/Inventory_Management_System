package com.springmfg.ims.wms.web;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.wms.domain.WmsEnums.WaveStatus;
import com.springmfg.ims.wms.dto.WmsDtos.CancelRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CreateWaveRequest;
import com.springmfg.ims.wms.dto.WmsDtos.PickListLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.springmfg.ims.wms.dto.WmsDtos.WaveDto;
import com.springmfg.ims.wms.service.PickingService;

/** Picking orders for sales orders and multi-order pick waves. */
@RestController
@RequestMapping("/api/wms")
public class PickingController {

    private final PickingService service;

    public PickingController(PickingService service) {
        this.service = service;
    }

    @PostMapping("/picking/sales-orders/{salesOrderId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PICKING_MANAGE')")
    public List<TransferOrderDto> pickSalesOrder(@PathVariable Long salesOrderId,
                                                 @RequestParam(required = false) Long warehouseId) {
        return service.createPickingTasks(salesOrderId, warehouseId);
    }

    @GetMapping("/waves")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public Page<WaveDto> listWaves(@RequestParam(required = false) WaveStatus status,
                                   @RequestParam(required = false) Long warehouseId,
                                   @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.searchWaves(status, warehouseId, pageable);
    }

    @GetMapping("/waves/{id}")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public WaveDto getWave(@PathVariable Long id) {
        return service.getWave(id);
    }

    @PostMapping("/waves")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WAVE_MANAGE')")
    public WaveDto createWave(@Valid @RequestBody CreateWaveRequest req) {
        return service.createWave(req);
    }

    @PostMapping("/waves/{id}/release")
    @PreAuthorize("hasAuthority('WAVE_MANAGE')")
    public WaveDto releaseWave(@PathVariable Long id) {
        return service.releaseWave(id);
    }

    @PostMapping("/waves/{id}/cancel")
    @PreAuthorize("hasAuthority('WAVE_MANAGE')")
    public WaveDto cancelWave(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancelWave(id, req.reason());
    }

    @GetMapping("/waves/{id}/pick-list")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public List<PickListLineDto> pickList(@PathVariable Long id, @RequestParam(required = false) Long zoneId) {
        return service.pickList(id, zoneId);
    }
}
