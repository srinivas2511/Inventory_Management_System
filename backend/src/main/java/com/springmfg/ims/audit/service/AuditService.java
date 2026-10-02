package com.springmfg.ims.audit.service;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.springmfg.ims.audit.domain.AuditLog;
import com.springmfg.ims.audit.dto.AuditLogDto;
import com.springmfg.ims.audit.repository.AuditLogRepository;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.Problems;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(Long userId, String entity, String action,
                                    Instant from, Instant to, Pageable pageable) {
        return repository.search(userId, entity, action, from, to, pageable).map(AuditLogDto::from);
    }

    public void record(String action, String entity, String entityId,
                       String oldValue, String newValue, String reason) {
        Long userId = null;
        String username = null;
        String roles = null;
        String ip = null;
        String correlationId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            userId = u.id();
            username = u.username();
            roles = String.join(",", u.roles());
        }

        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            ip = xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
            correlationId = MDC.get(Problems.TRACE_ID_KEY);
        } catch (IllegalStateException ignored) {
            // outside a web request (e.g. scheduled job)
        }

        repository.save(new AuditLog(userId, username, roles, action, entity, entityId,
            oldValue, newValue, reason, ip, correlationId));
    }
}
