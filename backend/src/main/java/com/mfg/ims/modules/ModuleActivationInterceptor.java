package com.mfg.ims.modules;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuses a request to an optional module an administrator has switched off, before the controller runs. Not a scanned
 * bean: {@link ModuleWebConfig} registers it when the module service exists, so controller-only test slices are unaffected.
 */
public class ModuleActivationInterceptor implements HandlerInterceptor {

    private final ModuleService modules;

    public ModuleActivationInterceptor(ModuleService modules) {
        this.modules = modules;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        modules.requireActiveFor(request.getRequestURI());
        return true;
    }
}
