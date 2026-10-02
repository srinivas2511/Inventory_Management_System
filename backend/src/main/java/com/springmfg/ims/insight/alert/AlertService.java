package com.springmfg.ims.insight.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.inventory.domain.Alert;
import com.springmfg.ims.inventory.repository.AlertRepository;

@Service
public class AlertService {

    private final AlertRepository repo;

    public AlertService(AlertRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Alert raise(String alertType, String severity, String entityType, Long entityId, String message) {
        List<Alert> existing = repo.findOpenByTypeAndEntity(alertType, entityType, entityId);
        if (!existing.isEmpty()) {
            Alert a = existing.get(0);
            a.setLastSeenAt(Instant.now());
            a.setMessage(message);
            return repo.save(a);
        }
        Alert a = new Alert();
        a.setAlertType(alertType);
        a.setSeverity(severity);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        String openKey = alertType + ":" + entityType + ":" + entityId;
        a.setOpenKey(openKey);
        a.setMessage(message);
        a.setStatus("OPEN");
        return repo.save(a);
    }

    @Transactional
    public AlertDto acknowledge(Long alertId, String note) {
        Alert a = repo.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId));
        Long userId = currentUserId();
        a.setAcknowledgedBy(userId);
        a.setAcknowledgedAt(Instant.now());
        a.setStatus("ACKNOWLEDGED");
        if (note != null) a.setMessage(a.getMessage() + " | Note: " + note);
        return AlertDto.from(repo.save(a));
    }

    @Transactional
    public AlertDto resolve(Long alertId, String note) {
        Alert a = repo.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId));
        Long userId = currentUserId();
        a.setResolvedAt(Instant.now());
        a.setResolvedBy(userId);
        a.setResolutionNote(note);
        a.setStatus("RESOLVED");
        return AlertDto.from(repo.save(a));
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> search(String status, String severity, String alertType, Pageable pageable) {
        return repo.search(status, severity, alertType, pageable).map(AlertDto::from);
    }

    @Transactional(readOnly = true)
    public AlertCountDto count() {
        long critical = repo.countBySeverityOpen("CRITICAL");
        long high = repo.countBySeverityOpen("HIGH");
        long medium = repo.countBySeverityOpen("MEDIUM");
        long low = repo.countBySeverityOpen("LOW");
        return new AlertCountDto(critical, high, medium, low, critical + high + medium + low);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
