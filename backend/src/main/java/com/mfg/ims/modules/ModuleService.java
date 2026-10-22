package com.mfg.ims.modules;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which optional modules are active. The API path of every optional module is listed here; anything not listed belongs
 * to the always-active core. The state is cached for a few seconds so the interceptor does not query on every request,
 * and the cache is cleared at once on the node that changes a module.
 */
@Service
@Transactional
public class ModuleService {

    /** Longest prefix wins. Paths outside this list are the core and are never gated. */
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("/api/ap", "FINANCE"), Map.entry("/api/ar", "FINANCE"), Map.entry("/api/gl", "FINANCE"),
            Map.entry("/api/journals", "FINANCE"), Map.entry("/api/periods", "FINANCE"),
            Map.entry("/api/bank", "FINANCE"), Map.entry("/api/finance", "FINANCE"),
            Map.entry("/api/config/budgets", "FINANCE"), Map.entry("/api/config/tax-codes", "FINANCE"),
            Map.entry("/api/controlling", "CONTROLLING"),
            Map.entry("/api/mrp", "MRP"),
            Map.entry("/api/crm", "CRM"),
            Map.entry("/api/hrm", "HRM"),
            Map.entry("/api/wms", "WMS"),
            Map.entry("/api/ecos", "PLM"), Map.entry("/api/product-lifecycle", "PLM"),
            Map.entry("/api/spc", "QUALITY_PLUS"), Map.entry("/api/capa", "QUALITY_PLUS"),
            Map.entry("/api/complaints", "QUALITY_PLUS"), Map.entry("/api/fai", "QUALITY_PLUS"),
            Map.entry("/api/documents", "DOCUMENTS"), Map.entry("/api/document-categories", "DOCUMENTS"),
            Map.entry("/api/oee", "ADV_MANUFACTURING"), Map.entry("/api/scheduling", "ADV_MANUFACTURING"),
            Map.entry("/api/spare-parts", "ADV_MANUFACTURING"),
            Map.entry("/api/analytics", "ANALYTICS"),
            Map.entry("/api/integration", "INTEGRATION"));

    private static final long CACHE_MILLIS = 5_000;

    public record ModuleDto(String code, String name, boolean active) {}

    private final ModuleActivationRepository modules;
    private final AuditService audit;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt;

    public ModuleService(ModuleActivationRepository modules, AuditService audit) {
        this.modules = modules;
        this.audit = audit;
    }

    /** The optional module an API path belongs to, or null for the always-active core. */
    public static String moduleOf(String path) {
        String best = null;
        int bestLength = -1;
        for (var entry : PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            boolean match = path.equals(prefix) || path.startsWith(prefix + "/");
            if (match && prefix.length() > bestLength) {
                best = entry.getValue();
                bestLength = prefix.length();
            }
        }
        return best;
    }

    @Transactional(readOnly = true)
    public boolean isActive(String moduleCode) {
        if (System.currentTimeMillis() - loadedAt > CACHE_MILLIS) {
            reload();
        }
        return cache.getOrDefault(moduleCode, Boolean.TRUE);
    }

    @Transactional(readOnly = true)
    public List<ModuleDto> list() {
        return modules.findAll().stream()
                .sorted(java.util.Comparator.comparing(ModuleActivation::getModuleCode))
                .map(m -> new ModuleDto(m.getModuleCode(), m.getName(), m.isActive())).toList();
    }

    public ModuleDto setActive(String code, boolean active) {
        ModuleActivation module = modules.findById(code)
                .orElseThrow(() -> new NotFoundException("Module", code));
        if (module.isActive() == active) {
            return new ModuleDto(code, module.getName(), active);
        }
        if (!active && "FINANCE".equals(code)) {
            // the ledger of record: other modules keep posting to it, so only its screens and API can be hidden
            audit.record("MODULE_FINANCE_DEACTIVATED", "MODULE", code, "ACTIVE", "INACTIVE",
                    "Finance screens and API are hidden; automatic postings continue");
        }
        module.setActive(active);
        module.setChangedBy(currentUserId());
        module.setChangedAt(Instant.now());
        modules.save(module);
        audit.record("MODULE_ACTIVATION", "MODULE", code, active ? "INACTIVE" : "ACTIVE", active ? "ACTIVE" : "INACTIVE", null);
        loadedAt = 0;
        return new ModuleDto(code, module.getName(), active);
    }

    /** Refuses with MODULE_INACTIVE when the path belongs to a module an administrator has switched off. */
    @Transactional(readOnly = true)
    public void requireActiveFor(String path) {
        String module = moduleOf(path);
        if (module != null && !isActive(module)) {
            throw new BusinessRuleException(ErrorCode.MODULE_INACTIVE,
                    "The " + module.toLowerCase().replace('_', ' ') + " module is not active. An administrator can activate it under Administration > Modules.");
        }
    }

    private void reload() {
        cache.clear();
        modules.findAll().forEach(m -> cache.put(m.getModuleCode(), m.isActive()));
        loadedAt = System.currentTimeMillis();
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
            return user.id();
        }
        return null;
    }
}
