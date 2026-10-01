package com.springmfg.ims.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.Problems;
import com.springmfg.ims.config.ImsSecurityProperties;

/**
 * Throttles {@code /api/auth/**} per client IP with a fixed window (ARCHITECTURE.md section 10.4): a generous
 * limit for sign-in traffic and a strict one for the e-mail-sending reset endpoints. Over the limit the answer is
 * 429 {@code RATE_LIMITED} with {@code Retry-After}. It is registered in the security filter chain by
 * {@code SecurityConfig}, not as a servlet filter bean.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/auth/";

    private final ObjectMapper objectMapper;
    private final int authLimit;
    private final Duration authWindow = Duration.ofMinutes(1);
    private final int resetLimit;
    private final Duration resetWindow;
    private final Cache<String, AtomicInteger> authCounters;
    private final Cache<String, AtomicInteger> resetCounters;

    public RateLimitFilter(ImsSecurityProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.authLimit = properties.rateLimit().authPerMinute();
        this.resetLimit = properties.rateLimit().resetPerWindow();
        this.resetWindow = properties.rateLimit().resetWindow();
        this.authCounters = Caffeine.newBuilder().expireAfterWrite(authWindow).maximumSize(50_000).build();
        this.resetCounters = Caffeine.newBuilder().expireAfterWrite(resetWindow).maximumSize(50_000).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX) || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean reset = path.endsWith("/forgot-password") || path.endsWith("/reset-password");
        Cache<String, AtomicInteger> counters = reset ? resetCounters : authCounters;
        int limit = reset ? resetLimit : authLimit;
        Duration window = reset ? resetWindow : authWindow;
        // expireAfterWrite: the entry is created on the first request and the window ends when it expires
        AtomicInteger count = counters.get(request.getRemoteAddr(), k -> new AtomicInteger());
        if (count.incrementAndGet() > limit) {
            ProblemDetail problem = Problems.of(ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
            response.setStatus(problem.getStatus());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }
}
