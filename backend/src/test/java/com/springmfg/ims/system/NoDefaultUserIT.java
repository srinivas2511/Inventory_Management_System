package com.springmfg.ims.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * E2E D-40 / SEC-09: Spring Boot's default user is created by UserDetailsServiceAutoConfiguration, and it writes a
 * generated password to the log at every start. Authentication here is stateless JWT, so the default user must not exist.
 * No UserDetailsService bean means that log line cannot be produced.
 */
class NoDefaultUserIT extends AbstractIntegrationTest {

    @Autowired ApplicationContext context;

    @Test
    @DisplayName("D-40 the application has no UserDetailsService, so no generated security password exists or is logged")
    void noDefaultUserDetailsService() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class))
                .as("a UserDetailsService bean (Spring Boot's default user) would log a generated password at start-up")
                .isEmpty();
        assertThat(context.containsBean("inMemoryUserDetailsManager")).isFalse();
    }

    @Test
    @DisplayName("D-40 HTTP Basic with a made-up credential is refused (the API accepts only a bearer token)")
    void basicAuthIsNotAccepted() throws Exception {
        String basic = Base64.getEncoder().encodeToString("user:not-a-real-password".getBytes());
        mockMvc.perform(get("/api/products").header("Authorization", "Basic " + basic))
                .andExpect(status().isUnauthorized());
    }
}
