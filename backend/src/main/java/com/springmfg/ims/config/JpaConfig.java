package com.springmfg.ims.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditor")
public class JpaConfig {

    /**
     * Supplies {@code created_by}/{@code updated_by}. Returns empty until authentication exists (Phase 1,
     * task 1.3 replaces this with the authenticated user id).
     */
    @Bean
    AuditorAware<Long> currentAuditor() {
        return Optional::empty;
    }
}
