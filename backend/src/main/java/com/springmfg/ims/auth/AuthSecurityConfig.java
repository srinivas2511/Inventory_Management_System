package com.springmfg.ims.auth;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthSecurityConfig {

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt, UserAccessService access) {
        return new JwtAuthenticationFilter(jwt, access);
    }

    /**
     * The filter belongs to the Spring Security chain only. Without this Spring Boot would also register it as a
     * plain servlet filter and run it twice, outside the chain's ordering.
     */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
