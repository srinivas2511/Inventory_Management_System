package com.springmfg.ims.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.springmfg.ims.common.exception.GlobalExceptionHandler;
import com.springmfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.springmfg.ims.config.CorrelationIdFilter;
import com.springmfg.ims.config.JwtAuthenticationFilter;
import com.springmfg.ims.config.ProblemDetailSecurityHandlers;
import com.springmfg.ims.config.SecurityConfig;
import com.springmfg.ims.config.SecurityHeadersFilter;

/**
 * GC-6 / SEC-004: the API sends the whole documented security header set - HSTS (2 years), X-Frame-Options,
 * X-Content-Type-Options, Referrer-Policy, Permissions-Policy and Content-Security-Policy - on successful responses
 * AND on 401 / 404 error responses, and keeps the Swagger paths free of the strict CSP so the dev UI still works.
 * Web slice only: it needs no Docker or database, so it runs in every environment.
 */
@WebMvcTest(controllers = SystemController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class, SecurityHeadersFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class SecurityHeadersTest {

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void stubJwtFilterAsPassThrough() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static ResultActions fullHeaderSet(ResultActions result) throws Exception {
        return result
                .andExpect(header().string("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));
    }

    @Test
    @DisplayName("SEC-004: a successful API response carries the full security header set")
    void successfulResponseCarriesEveryHeader() throws Exception {
        fullHeaderSet(mvc.perform(get("/api/system/ping")).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("SEC-004: a 401 response carries the full security header set")
    void unauthorisedResponseCarriesEveryHeader() throws Exception {
        fullHeaderSet(mvc.perform(get("/api/materials")).andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("SEC-004: HSTS lasts at least two years (63,072,000 seconds)")
    void hstsLastsTwoYears() throws Exception {
        mvc.perform(get("/api/system/ping"))
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.containsString("max-age=63072000")));
    }

    @Test
    @DisplayName("SEC-004: the Swagger UI paths do not get the strict API CSP")
    void swaggerPathsAreLeftToTheBrowserDefaults() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }
}
