package com.springmfg.ims.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.springmfg.ims.crm.service.CrmActivityService;
import com.springmfg.ims.crm.service.CrmContactService;
import com.springmfg.ims.crm.service.CrmLeadService;
import com.springmfg.ims.crm.service.CrmOpportunityService;
import com.springmfg.ims.crm.service.CrmQuotationService;
import com.springmfg.ims.crm.service.CustomerPriceService;
import com.springmfg.ims.crm.service.CustomerTagService;
import com.springmfg.ims.crm.web.CrmActivityController;
import com.springmfg.ims.crm.web.CrmContactController;
import com.springmfg.ims.crm.web.CrmLeadController;
import com.springmfg.ims.crm.web.CrmOpportunityController;
import com.springmfg.ims.crm.web.CrmQuotationController;
import com.springmfg.ims.crm.web.CustomerPriceController;
import com.springmfg.ims.crm.web.CustomerTagController;

/**
 * 403 matrix for every CRM endpoint (R12-3.3). Web slice only, so it runs without Docker. Each endpoint must
 * reject anonymous callers (401), reject callers holding any other authority (403) and accept the one authority
 * it declares. A final test fails when a controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { CrmContactController.class, CrmLeadController.class, CrmOpportunityController.class,
        CrmQuotationController.class, CrmActivityController.class, CustomerPriceController.class,
        CustomerTagController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class CrmSecurityMatrixTest {

    private static final String VIEW = "CRM_VIEW";
    private static final String LEAD = "LEAD_MANAGE";
    private static final String OPP = "OPPORTUNITY_MANAGE";
    private static final String QUOTE = "QUOTATION_CREATE";
    private static final String ACTIVITY = "CUSTOMER_ACTIVITY";
    private static final String PRICE = "PRICE_LIST_MANAGE";
    private static final String TAG = "CUSTOMER_TAG_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, LEAD, OPP, QUOTE, ACTIVITY, PRICE, TAG,
            "SALES_VIEW", "SALES_CREATE", "SALES_APPROVE", "MRP_VIEW", "REPORT_VIEW");

    private static final String CONTACT_BODY = "{\"firstName\":\"Asha\"}";
    private static final String LEAD_BODY = "{\"companyName\":\"Acme Springs\"}";
    private static final String DISQUALIFY_BODY = "{\"reason\":\"no budget\"}";
    private static final String OPP_BODY = "{\"description\":\"Compression springs\",\"customerId\":1}";
    private static final String ADVANCE_BODY = "{\"stage\":\"QUALIFIED\"}";
    private static final String QUOTE_BODY = "{\"customerId\":1,\"items\":[{\"productId\":1,\"quantity\":5}]}";
    private static final String REJECT_BODY = "{\"reason\":\"price too high\"}";
    private static final String ACTIVITY_BODY = "{\"activityType\":\"CALL\",\"subject\":\"Intro call\",\"customerId\":1}";
    private static final String PRICE_BODY = "{\"productId\":1,\"unitPrice\":10,\"validFrom\":\"2026-01-01\"}";
    private static final String TAG_BODY = "{\"tagGroup\":\"TIER\",\"name\":\"Gold\"}";
    private static final String CUSTOMER_TAGS_BODY = "{\"tagIds\":[1]}";

    record Endpoint(HttpMethod method, String template, String query, String authority, String body) {
        String url() {
            return query == null ? template : template + "?" + query;
        }

        @Override
        public String toString() {
            return method + " " + url() + " [" + authority + "]";
        }
    }

    static List<Endpoint> endpoints() {
        String contacts = "/api/crm/contacts";
        String leads = "/api/crm/leads";
        String opps = "/api/crm/opportunities";
        String quotes = "/api/crm/quotations";
        String acts = "/api/crm/activities";
        String prices = "/api/crm/prices";
        String tags = "/api/crm/tags";
        String customers = "/api/crm/customers";
        return List.of(
                new Endpoint(HttpMethod.GET, contacts, null, VIEW, null),
                new Endpoint(HttpMethod.GET, contacts + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, contacts, null, ACTIVITY, CONTACT_BODY),
                new Endpoint(HttpMethod.PUT, contacts + "/{id}", null, ACTIVITY, CONTACT_BODY),
                new Endpoint(HttpMethod.DELETE, contacts + "/{id}", null, ACTIVITY, null),

                new Endpoint(HttpMethod.GET, leads, null, VIEW, null),
                new Endpoint(HttpMethod.GET, leads + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, leads, null, LEAD, LEAD_BODY),
                new Endpoint(HttpMethod.PUT, leads + "/{id}", null, LEAD, LEAD_BODY),
                new Endpoint(HttpMethod.POST, leads + "/{id}/contact", null, LEAD, null),
                new Endpoint(HttpMethod.POST, leads + "/{id}/qualify", null, LEAD, null),
                new Endpoint(HttpMethod.POST, leads + "/{id}/disqualify", null, LEAD, DISQUALIFY_BODY),
                new Endpoint(HttpMethod.POST, leads + "/{id}/convert-to-opportunity", null, LEAD, "{}"),

                new Endpoint(HttpMethod.GET, opps, null, VIEW, null),
                new Endpoint(HttpMethod.GET, opps + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, opps, null, OPP, OPP_BODY),
                new Endpoint(HttpMethod.PUT, opps + "/{id}", null, OPP, OPP_BODY),
                new Endpoint(HttpMethod.POST, opps + "/{id}/advance", null, OPP, ADVANCE_BODY),

                new Endpoint(HttpMethod.GET, quotes, null, VIEW, null),
                new Endpoint(HttpMethod.GET, quotes + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, quotes, null, QUOTE, QUOTE_BODY),
                new Endpoint(HttpMethod.PUT, quotes + "/{id}", null, QUOTE, QUOTE_BODY),
                new Endpoint(HttpMethod.POST, quotes + "/{id}/send", null, QUOTE, null),
                new Endpoint(HttpMethod.POST, quotes + "/{id}/accept", null, QUOTE, null),
                new Endpoint(HttpMethod.POST, quotes + "/{id}/reject", null, QUOTE, REJECT_BODY),
                new Endpoint(HttpMethod.POST, quotes + "/{id}/convert-to-sales-order", null, QUOTE, null),

                new Endpoint(HttpMethod.GET, acts, null, VIEW, null),
                new Endpoint(HttpMethod.GET, acts + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, acts, null, ACTIVITY, ACTIVITY_BODY),
                new Endpoint(HttpMethod.PUT, acts + "/{id}", null, ACTIVITY, ACTIVITY_BODY),
                new Endpoint(HttpMethod.DELETE, acts + "/{id}", null, ACTIVITY, null),

                new Endpoint(HttpMethod.GET, prices, null, VIEW, null),
                new Endpoint(HttpMethod.GET, prices + "/resolve", "customerId=1&productId=1", VIEW, null),
                new Endpoint(HttpMethod.POST, prices, null, PRICE, PRICE_BODY),
                new Endpoint(HttpMethod.PUT, prices + "/{id}", null, PRICE, PRICE_BODY),
                new Endpoint(HttpMethod.DELETE, prices + "/{id}", null, PRICE, null),

                new Endpoint(HttpMethod.GET, tags, null, VIEW, null),
                new Endpoint(HttpMethod.POST, tags, null, TAG, TAG_BODY),
                new Endpoint(HttpMethod.PUT, tags + "/{id}", null, TAG, TAG_BODY),
                new Endpoint(HttpMethod.DELETE, tags + "/{id}", null, TAG, null),
                new Endpoint(HttpMethod.GET, customers, null, VIEW, null),
                new Endpoint(HttpMethod.GET, customers + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.PUT, customers + "/{id}/tags", null, TAG, CUSTOMER_TAGS_BODY));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean CrmContactService contactService;
    @MockBean CrmLeadService leadService;
    @MockBean CrmOpportunityService opportunityService;
    @MockBean CrmQuotationService quotationService;
    @MockBean CrmActivityService activityService;
    @MockBean CustomerPriceService priceService;
    @MockBean CustomerTagService tagService;

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
    }

    private ResultActions call(Endpoint e, String authority) throws Exception {
        var builder = request(e.method(), e.url(), 1L).with(csrf());
        if (authority != null) {
            builder = builder.with(user("tester").authorities(new SimpleGrantedAuthority(authority)));
        }
        if (e.body() != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return mvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} - anonymous gets 401")
    @MethodSource("endpointStream")
    void anonymousIsUnauthorized(Endpoint e) throws Exception {
        call(e, null).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} - any other authority gets 403")
    @MethodSource("endpointStream")
    void otherAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (other.equals(e.authority())) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority is accepted")
    @MethodSource("endpointStream")
    void declaredAuthorityIsAccepted(Endpoint e) throws Exception {
        call(e, e.authority()).andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryCrmEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(CrmContactController.class, CrmLeadController.class, CrmOpportunityController.class,
                CrmQuotationController.class, CrmActivityController.class, CustomerPriceController.class,
                CustomerTagController.class)) {
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
