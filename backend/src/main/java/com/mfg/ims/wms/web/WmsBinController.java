package com.mfg.ims.wms.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.wms.dto.WmsDtos.BinAssignmentDto;
import com.mfg.ims.wms.dto.WmsDtos.BinDto;
import com.mfg.ims.wms.dto.WmsDtos.BinStockLineDto;
import com.mfg.ims.wms.dto.WmsDtos.SaveAssignmentRequest;
import com.mfg.ims.wms.dto.WmsDtos.SaveBinRequest;
import com.mfg.ims.wms.service.BinService;

@RestController
@RequestMapping("/api/wms/bins")
public class WmsBinController {

    private final BinService service;

    public WmsBinController(BinService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public Page<BinDto> list(@RequestParam Long warehouseId, @RequestParam(required = false) Long zoneId,
                             @RequestParam(required = false) Boolean active, @RequestParam(required = false) String q,
                             @PageableDefault(size = 100, sort = "binCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.searchBins(warehouseId, zoneId, active, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public BinDto get(@PathVariable Long id) {
        return service.getBin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public BinDto create(@Valid @RequestBody SaveBinRequest req) {
        return service.createBin(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public BinDto update(@PathVariable Long id, @Valid @RequestBody SaveBinRequest req) {
        return service.updateBin(id, req);
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public List<BinStockLineDto> stock(@PathVariable Long id) {
        return service.binStock(id);
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public List<BinAssignmentDto> assignments(@PathVariable Long id) {
        return service.listAssignments(id);
    }

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public BinAssignmentDto assign(@PathVariable Long id, @Valid @RequestBody SaveAssignmentRequest req) {
        return service.addAssignment(id, req);
    }

    @DeleteMapping("/{id}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public void unassign(@PathVariable Long id, @PathVariable Long assignmentId) {
        service.removeAssignment(id, assignmentId);
    }
}
