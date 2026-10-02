package com.springmfg.ims.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.GlobalExceptionHandler;
import com.springmfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.springmfg.ims.config.CorrelationIdFilter;
import com.springmfg.ims.config.JwtAuthenticationFilter;
import com.springmfg.ims.config.ProblemDetailSecurityHandlers;
import com.springmfg.ims.config.SecurityConfig;

/**
 * Security and error-contract baseline. Runs without Docker or a database (web slice only), so it always
 * runs in every environment.
 */
@WebMvcTest(controllers = { SystemController.class, SecurityBaselineTest.Probe.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class, SecurityBaselineTest.Probe.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class SecurityBaselineTest {

    @RestController
    public static class Probe {
        @GetMapping("/api/probe/secret")
        @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
        public String secret() {
            return "secret";
        }

        @GetMapping("/api/probe/rule")
        @PreAuthorize("isAuthenticated()")
        public String rule() {
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK, "Requested 400 KG; available 250 KG");
        }

        @PostMapping("/api/probe/boom")
        @PreAuthorize("isAuthenticated()")
        public String boom() {
            throw new IllegalStateException("internal detail that must never leak");
        }
    }

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

    @Test
    void publicPingNeedsNoToken() throws Exception {
        mvc.perform(get("/api/system/ping")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unauthenticatedCallGets401Problem() throws Exception {
        mvc.perform(get("/api/probe/secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_VIEW")
    void authenticatedUserWithoutPermissionGets403Problem() throws Exception {
        mvc.perform(get("/api/probe/secret"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_UPDATE")
    void authenticatedUserWithPermissionIsAllowed() throws Exception {
        mvc.perform(get("/api/probe/secret")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void businessRuleViolationIs422WithStableCode() throws Exception {
        mvc.perform(get("/api/probe/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.detail").value("Requested 400 KG; available 250 KG"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @WithMockUser
    void unexpectedErrorsNeverLeakInternals() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/probe/boom")
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/system/ping")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/system/ping")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
