package com.springmfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springmfg.ims.common.exception.GlobalExceptionHandler;
import com.springmfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.springmfg.ims.config.CorrelationIdFilter;
import com.springmfg.ims.config.JwtAuthenticationFilter;
import com.springmfg.ims.config.ProblemDetailSecurityHandlers;
import com.springmfg.ims.config.SecurityConfig;
import com.springmfg.ims.document.service.DocumentService;
import com.springmfg.ims.document.service.DocumentService.DocumentDownload;
import com.springmfg.ims.document.web.DocumentCategoryController;
import com.springmfg.ims.document.web.DocumentController;

/**
 * 403 matrix for every document endpoint. Web slice only, so it runs without Docker: anonymous callers get 401,
 * authorities an endpoint does not declare get 403, each declared authority is accepted. A final test fails when a
 * controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { DocumentController.class, DocumentCategoryController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class DocumentSecurityMatrixTest {

    private static final String VIEW = "DOCUMENT_VIEW";
    private static final String UPLOAD = "DOCUMENT_UPLOAD";
    private static final String APPROVE = "DOCUMENT_APPROVE";
    private static final String MANAGE = "DOCUMENT_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, UPLOAD, APPROVE, MANAGE, "DRAWING_MANAGE",
            "SPECIFICATION_MANAGE", "PRODUCT_VIEW", "PLM_VIEW", "QUALITY_VIEW", "NOTIFICATION_MANAGE", "HR_VIEW",
            "USER_UPDATE");

    private static final String META = "{\"categoryCode\":\"SOP\",\"title\":\"Coiling SOP\"}";
    private static final String VERSION_META = "{\"revisionLabel\":\"B\"}";
    private static final String LINK_BODY = "{\"entityType\":\"PRODUCT\",\"entityId\":1}";
    private static final String REASON_BODY = "{\"reason\":\"superseded\"}";
    private static final String CATEGORY_BODY = "{\"categoryCode\":\"MANUAL\",\"name\":\"Manual\"}";

    /** A body of MULTIPART means a multipart/form-data request with a "meta" JSON part and a "file" part. */
    private static final String MULTIPART = "multipart";

    record Endpoint(HttpMethod method, String template, Set<String> authorities, String body) {
        @Override
        public String toString() {
            return method + " " + template + " " + authorities;
        }
    }

    private static Endpoint ep(HttpMethod m, String template, String body, String... authorities) {
        return new Endpoint(m, template, Set.of(authorities), body);
    }

    static List<Endpoint> endpoints() {
        String d = "/api/documents";
        String c = "/api/document-categories";
        return List.of(
                ep(HttpMethod.GET, d, null, VIEW),
                ep(HttpMethod.GET, d + "/{id}", null, VIEW),
                ep(HttpMethod.POST, d, MULTIPART, UPLOAD),
                ep(HttpMethod.POST, d + "/{id}/versions", MULTIPART, UPLOAD),
                ep(HttpMethod.GET, d + "/{id}/download", null, VIEW),
                ep(HttpMethod.POST, d + "/{id}/links", LINK_BODY, UPLOAD),
                ep(HttpMethod.DELETE, d + "/{id}/links/{linkId}", null, UPLOAD),
                ep(HttpMethod.POST, d + "/{id}/submit", null, UPLOAD),
                ep(HttpMethod.POST, d + "/{id}/approve", null, APPROVE),
                ep(HttpMethod.POST, d + "/{id}/reject", REASON_BODY, APPROVE),
                ep(HttpMethod.POST, d + "/{id}/obsolete", REASON_BODY, APPROVE),

                ep(HttpMethod.GET, c, null, VIEW),
                ep(HttpMethod.POST, c, CATEGORY_BODY, MANAGE),
                ep(HttpMethod.PUT, c + "/{id}", CATEGORY_BODY, MANAGE));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    static Stream<Object[]> declaredAuthorityStream() {
        return endpoints().stream().flatMap(e -> e.authorities().stream().map(a -> new Object[] { e, a }));
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean DocumentService documentService;

    @Autowired MockMvc mvc;

    @BeforeEach
    void stubJwtFilterAsPassThrough() throws Exception {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        when(documentService.download(any(), any())).thenAnswer(inv ->
                new DocumentDownload("a.pdf", "application/pdf", 1, new ByteArrayInputStream(new byte[] { 1 })));
    }

    private ResultActions call(Endpoint e, String authority) throws Exception {
        var builder = MULTIPART.equals(e.body())
                ? multipart(e.method(), e.template(), 1L, 1L)
                        .file(new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
                                (e.template().endsWith("/versions") ? VERSION_META : META).getBytes()))
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] { 1 }))
                : request(e.method(), e.template(), 1L, 1L);
        builder = (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) builder.with(csrf());
        if (authority != null) {
            builder = (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) builder
                    .with(user("tester").authorities(new SimpleGrantedAuthority(authority)));
        }
        if (e.body() != null && !MULTIPART.equals(e.body())) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return mvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} - anonymous gets 401")
    @MethodSource("endpointStream")
    void anonymousIsUnauthorized(Endpoint e) throws Exception {
        call(e, null).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} - any undeclared authority gets 403")
    @MethodSource("endpointStream")
    void undeclaredAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority {1} is accepted")
    @MethodSource("declaredAuthorityStream")
    void declaredAuthorityIsAccepted(Endpoint e, String authority) throws Exception {
        call(e, authority).andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryDocumentEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(DocumentController.class, DocumentCategoryController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0];
            for (Method m : c.getDeclaredMethods()) {
                String key = key(m, base);
                if (key != null) declared.add(key);
            }
        }
        Set<String> covered = new HashSet<>();
        for (Endpoint e : endpoints()) {
            covered.add(e.method() + " " + e.template().replaceAll("\\{[^}]+}", "{}"));
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(declared);
    }

    private static String key(Method m, String base) {
        String verb = null;
        String[] paths = {};
        if (m.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            paths = m.getAnnotation(GetMapping.class).value();
        } else if (m.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            paths = m.getAnnotation(PostMapping.class).value();
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            paths = m.getAnnotation(PutMapping.class).value();
        } else if (m.isAnnotationPresent(DeleteMapping.class)) {
            verb = "DELETE";
            paths = m.getAnnotation(DeleteMapping.class).value();
        }
        if (verb == null) return null;
        String path = base + (paths.length == 0 ? "" : paths[0]);
        return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}
