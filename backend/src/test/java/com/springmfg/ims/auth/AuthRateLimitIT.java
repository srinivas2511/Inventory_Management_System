package com.springmfg.ims.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.springmfg.ims.support.AbstractIntegrationTest;

/** Throttling is wired into the real security filter chain (the filter itself is unit-tested). */
@TestPropertySource(properties = { "ims.security.rate-limit.auth-per-minute=3", "ims.security.rate-limit.reset-per-window=2" })
class AuthRateLimitIT extends AbstractIntegrationTest {

    private static final String BODY = "{\"username\":\"nobody\",\"password\":\"Wrong-Pass-1234!\"}";

    @Test
    void loginIsThrottledPerClientAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void forgotPasswordHasAStricterLimit() throws Exception {
        String body = "{\"email\":\"nobody@example.com\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
