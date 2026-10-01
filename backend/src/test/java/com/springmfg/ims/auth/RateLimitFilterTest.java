package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.config.ImsSecurityProperties;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new ImsSecurityProperties("x".repeat(32),
            Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(30), true,
            new ImsSecurityProperties.RateLimit(3, 2, Duration.ofMinutes(15))), new ObjectMapper());

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsUpToTheLimitThenAnswers429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/api/auth/login", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void limitsAreCountedPerClientAddress() throws Exception {
        for (int i = 0; i < 4; i++) {
            call("POST", "/api/auth/login", "10.0.0.2");
        }
        assertThat(call("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/login", "10.0.0.3").getStatus()).isEqualTo(200);
    }

    @Test
    void resetEndpointsHaveAStricterLimit() throws Exception {
        assertThat(call("POST", "/api/auth/forgot-password", "10.0.0.4").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/reset-password", "10.0.0.4").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = call("POST", "/api/auth/forgot-password", "10.0.0.4");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("900");
    }

    @Test
    void otherPathsAndPreflightAreNotThrottled() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/system/ping", "10.0.0.5").getStatus()).isEqualTo(200);
            assertThat(call("OPTIONS", "/api/auth/login", "10.0.0.5").getStatus()).isEqualTo(200);
        }
    }
}
