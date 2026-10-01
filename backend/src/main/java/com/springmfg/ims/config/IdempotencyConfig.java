package com.springmfg.ims.config;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.CurrentUser;
import com.springmfg.ims.common.idempotency.IdempotencyFilter;
import com.springmfg.ims.common.idempotency.IdempotencyIdentity;
import com.springmfg.ims.common.idempotency.IdempotencyRequirement;
import com.springmfg.ims.common.idempotency.IdempotencyStore;

/** Wires the idempotency filter (which lives in {@code common}) to the security module. */
@Configuration
class IdempotencyConfig {

    /** An unfinished claim older than this is presumed to belong to a crashed request. */
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);

    @Bean
    IdempotencyIdentity idempotencyIdentity() {
        return CurrentUser::id;
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new IdempotencyStore(jdbc, json, ABANDONED_AFTER);
    }

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyStore store, IdempotencyIdentity identity,
            ObjectProvider<IdempotencyRequirement> requirements, ObjectMapper json) {
        return new IdempotencyFilter(store, identity, requirements.orderedStream().toList(), json);
    }

    /** It belongs to the Spring Security chain only (after the JWT filter), not to the servlet container's. */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
