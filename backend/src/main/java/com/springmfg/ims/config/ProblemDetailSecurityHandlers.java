package com.springmfg.ims.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.JwtAuthenticationFilter;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.Problems;

/**
 * Writes 401 and 403 responses produced by the security filter chain in the same RFC 7807 shape as the rest of
 * the API (the controller advice cannot see exceptions raised before a controller is reached).
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // the JWT filter records why a presented token was refused (e.g. TOKEN_EXPIRED -> the client refreshes)
        Object reason = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
        if (reason == ErrorCode.TOKEN_EXPIRED) {
            write(response, Problems.of(ErrorCode.TOKEN_EXPIRED, "Your access token has expired. Refresh it."));
        } else {
            write(response, Problems.of(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, Problems.of(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action."));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
