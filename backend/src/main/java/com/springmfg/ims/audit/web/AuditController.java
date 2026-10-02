package com.springmfg.ims.audit.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.audit.dto.AuditLogDto;
import com.springmfg.ims.audit.service.AuditService;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IAM_AUDIT_VIEW')")
    public Page<AuditLogDto> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        return auditService.search(userId, entity, action, from, to, pageable);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('IAM_AUDIT_VIEW')")
    public void export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-log.csv\"");
        PrintWriter writer = response.getWriter();
        writer.println("id,occurredAt,userId,username,action,entity,entityId,oldValue,newValue,reason,ipAddress");
        int page = 0;
        Page<AuditLogDto> p;
        do {
            p = auditService.search(userId, entity, action, from, to, PageRequest.of(page++, 500));
            for (AuditLogDto l : p.getContent()) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    l.id(), l.occurredAt(), nvl(l.userId()), nvl(l.username()),
                    nvl(l.action()), nvl(l.entity()), nvl(l.entityId()),
                    esc(l.oldValue()), esc(l.newValue()), esc(l.reason()), nvl(l.ipAddress()));
            }
        } while (p.hasNext());
        writer.flush();
    }

    private static String nvl(Object o) { return o == null ? "" : o.toString(); }
    private static String esc(String s) { return s == null ? "" : s.replace("\"", "\"\""); }
}
