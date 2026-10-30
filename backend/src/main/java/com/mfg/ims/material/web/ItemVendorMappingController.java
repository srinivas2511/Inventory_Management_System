package com.mfg.ims.material.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.material.dto.ItemVendorMappingDto;
import com.mfg.ims.material.service.ItemVendorMappingService;

@RestController
@RequestMapping("/api/materials/{itemId}/vendor-mappings")
public class ItemVendorMappingController {

    private final ItemVendorMappingService service;

    public ItemVendorMappingController(ItemVendorMappingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ITEM_VENDOR_VIEW','ITEM_VENDOR_MANAGE')")
    public List<ItemVendorMappingDto> list(@PathVariable Long itemId) {
        return service.listByItem(itemId);
    }

    public record CreateMappingRequest(
            Long supplierId,
            String supplierItemCode,
            String supplierItemName,
            BigDecimal lastPrice,
            BigDecimal moq,
            int leadTimeDays,
            boolean preferred
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ITEM_VENDOR_MANAGE')")
    public ItemVendorMappingDto create(@PathVariable Long itemId,
                                       @RequestBody CreateMappingRequest body) {
        return service.create(itemId, body.supplierId(), body.supplierItemCode(),
                body.supplierItemName(), body.lastPrice(), body.moq(),
                body.leadTimeDays(), body.preferred());
    }

    public record UpdateMappingRequest(
            String supplierItemCode,
            String supplierItemName,
            BigDecimal lastPrice,
            BigDecimal moq,
            int leadTimeDays,
            boolean preferred
    ) {}

    @PutMapping("/{mappingId}")
    @PreAuthorize("hasAuthority('ITEM_VENDOR_MANAGE')")
    public ItemVendorMappingDto update(@PathVariable Long itemId,
                                        @PathVariable Long mappingId,
                                        @RequestBody UpdateMappingRequest body) {
        return service.update(mappingId, body.supplierItemCode(), body.supplierItemName(),
                body.lastPrice(), body.moq(), body.leadTimeDays(), body.preferred());
    }

    @DeleteMapping("/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ITEM_VENDOR_MANAGE')")
    public void delete(@PathVariable Long itemId, @PathVariable Long mappingId) {
        service.delete(mappingId);
    }
}
