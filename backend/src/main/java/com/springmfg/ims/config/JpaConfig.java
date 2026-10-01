package com.springmfg.ims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.springmfg.ims.auth.CurrentUser;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditor")
public class JpaConfig {

    /** Supplies {@code created_by}/{@code updated_by}: the signed-in user, empty for system and unauthenticated work. */
    @Bean
    AuditorAware<Long> currentAuditor() {
        return CurrentUser::id;
    }
}
