package com.springmfg.ims.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.JwtAuthenticationFilter;
import com.springmfg.ims.auth.RateLimitFilter;
import com.springmfg.ims.common.idempotency.IdempotencyFilter;

/**
 * Security baseline: stateless, deny-by-default, uniform 401/403 problem responses, strict CORS, throttled
 * authentication endpoints. Everything except the explicitly listed public endpoints needs a valid bearer token
 * ({@code JwtAuthenticationFilter}); what an authenticated user may do is decided per endpoint by
 * {@code @PreAuthorize} (see DESIGN.md section 7.4).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({ ImsSecurityProperties.class, ImsMailProperties.class, BootstrapAdminProperties.class })
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/api/system/ping",
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/change-password",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandlers problemHandlers,
            ImsSecurityProperties securityProperties, ObjectMapper objectMapper,
            ObjectProvider<JwtAuthenticationFilter> jwtFilter, ObjectProvider<IdempotencyFilter> idempotencyFilter)
            throws Exception {
        // filter order (DESIGN 7.3): correlation id -> rate limit -> JWT -> idempotency -> method security in the
        // controllers. Filters added before the same anchor run in the order they are added here.
        http.addFilterBefore(new RateLimitFilter(securityProperties, objectMapper), BasicAuthenticationFilter.class);
        jwtFilter.ifAvailable(filter -> http.addFilterBefore(filter, BasicAuthenticationFilter.class));
        idempotencyFilter.ifAvailable(filter -> http.addFilterBefore(filter, BasicAuthenticationFilter.class));
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless token API, no cookies for auth on /api
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /** Authentication is token-based; this stops Spring Boot generating a default user/password. */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Form/basic login is not supported");
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${ims.security.cors-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(allowed); // explicit allow-list; never "*" together with credentials
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Location", "Idempotent-Replayed", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
