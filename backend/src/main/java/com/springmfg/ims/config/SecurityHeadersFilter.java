package com.springmfg.ims.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds security response headers not covered by Spring Security's DSL defaults:
 * HSTS (on HTTPS), legacy XSS-Protection, and Permissions-Policy.
 * Placed at highest precedence so headers are always set before the response flushes.
 */
@Component
@Order(Integer.MIN_VALUE)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResp) {
            // 2-year HSTS with subdomains; only takes effect over HTTPS
            httpResp.setHeader("Strict-Transport-Security",
                    "max-age=63072000; includeSubDomains; preload");

            // Legacy IE / old-Chrome protection (safe to include; modern browsers ignore it)
            httpResp.setHeader("X-XSS-Protection", "1; mode=block");

            // Lock down browser feature APIs not used by this application
            httpResp.setHeader("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        }
        chain.doFilter(request, response);
    }
}
