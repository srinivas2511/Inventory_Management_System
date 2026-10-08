package com.springmfg.ims.modules;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ModuleWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<ModuleService> modules;

    public ModuleWebConfig(ObjectProvider<ModuleService> modules) {
        this.modules = modules;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ModuleService service = modules.getIfAvailable();
        if (service != null) {
            registry.addInterceptor(new ModuleActivationInterceptor(service)).addPathPatterns("/api/**");
        }
    }
}
