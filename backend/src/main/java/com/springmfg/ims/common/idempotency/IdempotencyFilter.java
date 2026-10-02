package com.springmfg.ims.common.idempotency;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * Enforces idempotency for POST/PUT/PATCH requests that supply an
 * {@code Idempotency-Key} header. On a replay with the same key and
 * identical request hash, returns the stored response. On a replay with
 * a different body, returns 409 IDEMPOTENCY_REPLAY_MISMATCH.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER = "Idempotency-Key";

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !(method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))
            || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER);
        ContentCachingRequestWrapper wrappedReq = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResp = new ContentCachingResponseWrapper(response);

        chain.doFilter(wrappedReq, wrappedResp);

        byte[] body = wrappedReq.getContentAsByteArray();
        String requestHash = sha256(body);
        Long userId = resolveUserId();

        repository.findById(idempotencyKey).ifPresentOrElse(
            existing -> {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_REPLAY_MISMATCH,
                        "Idempotency key reused with a different request body");
                }
                // replay stored response
                try {
                    response.setStatus(existing.getResponseStatus());
                    if (existing.getResponseBody() != null) {
                        response.setContentType("application/json");
                        response.getWriter().write(existing.getResponseBody());
                    }
                    response.flushBuffer();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            () -> {
                // first time — persist result
                try {
                    IdempotencyKey record = new IdempotencyKey(idempotencyKey, userId, requestHash);
                    record.setResponseStatus(wrappedResp.getStatus());
                    byte[] respBody = wrappedResp.getContentAsByteArray();
                    if (respBody.length > 0) {
                        record.setResponseBody(new String(respBody, StandardCharsets.UTF_8));
                    }
                    repository.save(record);
                    wrappedResp.copyBodyToResponse();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }

    private static String sha256(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Long resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return -1L;
    }
}
