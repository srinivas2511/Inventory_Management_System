package com.springmfg.ims.modules;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuses a request to an optional module an administrator has switched off, before the controller runs. */
@Component
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
