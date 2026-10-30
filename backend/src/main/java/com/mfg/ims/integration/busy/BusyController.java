package com.mfg.ims.integration.busy;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busy accounting full-sync endpoints (16.3).
 * Separate from BusyExportController to avoid path conflicts.
 */
@RestController
@RequestMapping("/api/integration/busy")
public class BusyController {

    private final BusySyncService syncService;

    public BusyController(BusySyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public BusySyncService.SyncResult sync() {
        return syncService.sync();
    }

    @GetMapping("/sync-log")
    @PreAuthorize("hasAuthority('BUSY_EXPORT_VIEW')")
    public List<Map<String, Object>> syncLog() {
        return syncService.syncLog();
    }
}
