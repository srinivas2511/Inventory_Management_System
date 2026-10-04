package com.springmfg.ims.sales.web;

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

import com.springmfg.ims.sales.dto.AvailableBatchDto;
import com.springmfg.ims.sales.dto.ReservationDto;
import com.springmfg.ims.sales.dto.ReserveRequest;
import com.springmfg.ims.sales.service.ReservationService;

@RestController
@RequestMapping("/api/sales/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','RESERVE_FG')")
    public ReservationDto reserve(@Valid @RequestBody ReserveRequest req) {
        return service.reserve(req);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','RESERVE_FG')")
    public List<ReservationDto> listForItem(@RequestParam Long salesOrderItemId) {
        return service.findByOrderItem(salesOrderItemId);
    }

    @GetMapping("/available-batches")
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','RESERVE_FG')")
    public List<AvailableBatchDto> availableBatches(@RequestParam Long productId) {
        return service.findAvailableBatches(productId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('RESERVE_FG','SALES_APPROVE')")
    public void release(@PathVariable Long id) {
        service.releaseReservation(id);
    }
}
