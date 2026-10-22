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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.wms.domain.WmsEnums.MovementType;
import com.mfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.mfg.ims.wms.dto.WmsDtos.AssignRequest;
import com.mfg.ims.wms.dto.WmsDtos.CancelRequest;
import com.mfg.ims.wms.dto.WmsDtos.ConfirmRequest;
import com.mfg.ims.wms.dto.WmsDtos.ConfirmResultDto;
import com.mfg.ims.wms.dto.WmsDtos.CreateTransferRequest;
import com.mfg.ims.wms.dto.WmsDtos.CrossDockRequest;
import com.mfg.ims.wms.dto.WmsDtos.SetTargetRequest;
import com.mfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.mfg.ims.wms.service.PutAwayService;
import com.mfg.ims.wms.service.TransferOrderService;

/**
 * Transfer orders. Executing one (assign, start, confirm, cancel, set target) needs the permission that owns its
 * movement type; the endpoint admits the union and the service checks the specific one.
 */
@RestController
@RequestMapping("/api/wms/transfer-orders")
public class TransferOrderController {

    private static final String EXECUTE = "hasAnyAuthority('PUTAWAY_MANAGE','PICKING_MANAGE','TRANSFER_MANAGE')";

    private final TransferOrderService service;
    private final PutAwayService putAway;

    public TransferOrderController(TransferOrderService service, PutAwayService putAway) {
        this.service = service;
        this.putAway = putAway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public Page<TransferOrderDto> list(@RequestParam(required = false) TransferOrderStatus status,
                                       @RequestParam(required = false) MovementType movementType,
                                       @RequestParam(required = false) Long warehouseId,
                                       @RequestParam(required = false) Long assignedTo,
                                       @RequestParam(required = false) Long waveId,
                                       @RequestParam(required = false) String q,
                                       @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, movementType, warehouseId, assignedTo, waveId, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public TransferOrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSFER_MANAGE')")
    public TransferOrderDto createInternal(@Valid @RequestBody CreateTransferRequest req) {
        return service.createInternalTransfer(req);
    }

    @PostMapping("/putaway/from-receipt/{goodsReceiptId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PUTAWAY_MANAGE')")
    public TransferOrderDto putAwayFromReceipt(@PathVariable Long goodsReceiptId) {
        return putAway.putAwayForReceipt(goodsReceiptId);
    }

    @PostMapping("/cross-dock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PUTAWAY_MANAGE')")
    public TransferOrderDto crossDock(@Valid @RequestBody CrossDockRequest req) {
        return putAway.crossDock(req);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(EXECUTE)
    public TransferOrderDto assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return service.assign(id, req.userId());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(EXECUTE)
    public TransferOrderDto start(@PathVariable Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/set-target")
    @PreAuthorize(EXECUTE)
    public TransferOrderDto setTarget(@PathVariable Long id, @Valid @RequestBody SetTargetRequest req) {
        return service.setTarget(id, req.lineId(), req.toBinId());
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(EXECUTE)
    public ConfirmResultDto confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRequest req) {
        return service.confirm(id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(EXECUTE)
    public TransferOrderDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }
}
