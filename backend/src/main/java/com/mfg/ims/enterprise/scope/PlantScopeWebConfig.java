package com.mfg.ims.enterprise.scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlantScopeWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<PlantScope> scope;

    public PlantScopeWebConfig(ObjectProvider<PlantScope> scope) {
        this.scope = scope;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        PlantScope plantScope = scope.getIfAvailable();
        if (plantScope != null) {
            registry.addInterceptor(new PlantScopeGuardInterceptor(plantScope)).addPathPatterns("/api/**");
        }
    }
}
