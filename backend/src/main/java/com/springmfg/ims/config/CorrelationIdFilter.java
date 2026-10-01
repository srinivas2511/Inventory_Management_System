package com.springmfg.ims.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.springmfg.ims.common.exception.Problems;

/**
 * Assigns every request a correlation id (honouring a sane incoming {@code X-Correlation-Id}), exposes it in the
 * logging MDC as {@code traceId}, and echoes it on the response so support can find the matching log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = (incoming != null && SAFE.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(Problems.TRACE_ID_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Problems.TRACE_ID_KEY);
        }
    }
}
