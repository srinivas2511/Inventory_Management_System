package com.springmfg.ims.audit;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The audit log is read-only through the API; rows are written only by the application and cannot be altered. */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogQueryService service;

    AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Operation(summary = "Search the audit log, newest first (filters combine with AND; from/to are ISO-8601 instants)")
    public PageResponse<AuditLogDtos.AuditLogResponse> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return service.search(new AuditLogQueryService.Filter(userId, username, entity, entityId, action, from, to), pageable);
    }
}
