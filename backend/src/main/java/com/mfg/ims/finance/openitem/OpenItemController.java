package com.mfg.ims.finance.openitem;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/open-items")
public class OpenItemController {

    private final OpenItemService service;

    public OpenItemController(OpenItemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OPEN_ITEM_VIEW')")
    public List<OpenItemDto> list(@RequestParam(required = false) String partyType,
                                   @RequestParam(required = false) Long partyId) {
        if (partyType != null && partyId != null) {
            return service.listOpen(partyType, partyId);
        }
        return service.ageingReport();
    }
}
