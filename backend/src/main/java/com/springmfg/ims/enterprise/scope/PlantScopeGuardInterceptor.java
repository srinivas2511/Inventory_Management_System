package com.springmfg.ims.enterprise.scope;

import java.util.List;

import org.springframework.web.servlet.HandlerInterceptor;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Keeps plant-scoped users out of the functions that aggregate or administer across plants (dashboards, reports,
 * analytics, traceability, finance, controlling, planning and the audit trail). Their queries are not per plant, so
 * serving them would show other plants' figures; users who need them hold PLANT_ALL_ACCESS. Not a scanned bean:
 * {@link PlantScopeWebConfig} registers it when the scope helper exists, so controller-only test slices are unaffected.
 */
public class PlantScopeGuardInterceptor implements HandlerInterceptor {

    /** Company-level API prefixes a plant-scoped user may not call. */
    static final List<String> COMPANY_LEVEL = List.of(
            "/api/dashboard", "/api/reports", "/api/analytics", "/api/traceability", "/api/integration",
            "/api/finance", "/api/ap", "/api/ar", "/api/gl", "/api/journals", "/api/periods", "/api/bank",
            "/api/config", "/api/controlling", "/api/mrp", "/api/audit-logs", "/api/hrm/payroll");

    private final PlantScope scope;

    public PlantScopeGuardInterceptor(PlantScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!scope.isScoped()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : COMPANY_LEVEL) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                throw new BusinessRuleException(ErrorCode.PLANT_SCOPE_COMPANY_LEVEL,
                        "This function covers all plants and is not available to a user limited to specific plants");
            }
        }
        return true;
    }
}
