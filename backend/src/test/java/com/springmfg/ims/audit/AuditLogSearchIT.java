package com.springmfg.ims.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * {@code GET /api/audit-logs} against real PostgreSQL. Every optional filter (Long, String, Instant) used to be
 * written as {@code :x IS NULL OR col = :x}; an untyped null made PostgreSQL answer "could not determine data type
 * of parameter" (HTTP 500) whenever a filter was absent. Each filter is exercised absent, alone and combined.
 */
@Transactional
class AuditLogSearchIT extends AbstractIntegrationTest {

    private static final long USER_A = 7101L;
    private static final long USER_B = 7102L;

    @Autowired AuditService auditService;
    @Autowired ObjectMapper mapper;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private String entityA;
    private String entityB;

    @BeforeEach
    void seedAuditRows() {
        entityA = "AUD_A_" + run;
        entityB = "AUD_B_" + run;
        recordAs(USER_A, "CREATE", entityA);
        recordAs(USER_A, "UPDATE", entityA);
        recordAs(USER_B, "DELETE", entityB);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void recordAs(long userId, String action, String entity) {
        var actor = new CachedUser(userId, "audit-it-" + userId, true, 1, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null));
        auditService.record(action, entity, "1", null, "\"x\"", "audit-it");
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder request(String path, String... nameValuePairs) {
        MockHttpServletRequestBuilder req = get(path)
                .with(user("audit-reader").authorities(new SimpleGrantedAuthority("AUDIT_VIEW")));
        boolean sizeGiven = false;
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            sizeGiven |= "size".equals(nameValuePairs[i]);
            req = req.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        // other ITs leave committed audit rows behind, so the default page size could hide the rows under test
        return sizeGiven ? req : req.param("size", "1000");
    }

    private JsonNode search(String... nameValuePairs) throws Exception {
        MvcResult result = mockMvc.perform(request("/api/audit-logs", nameValuePairs)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET /api/audit-logs").isEqualTo(200);
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> actions(JsonNode page) {
        return page.get("content").findValuesAsText("action");
    }

    @Test
    void noFiltersReturnsEveryRow() throws Exception {
        JsonNode page = search();
        assertThat(page.get("content").findValuesAsText("entity")).contains(entityA, entityB);
    }

    @Test
    void entityFilterAlone() throws Exception {
        assertThat(actions(search("entity", entityA))).containsExactlyInAnyOrder("CREATE", "UPDATE");
        assertThat(actions(search("entity", entityB))).containsExactly("DELETE");
        assertThat(actions(search("entity", "NO_SUCH_" + run))).isEmpty();
    }

    @Test
    void actionFilterAlone() throws Exception {
        JsonNode page = search("action", "DELETE");
        assertThat(page.get("content").findValuesAsText("entity")).contains(entityB).doesNotContain(entityA);
    }

    @Test
    void userFilterAlone() throws Exception {
        JsonNode page = search("userId", String.valueOf(USER_B));
        assertThat(page.get("content").findValuesAsText("entity")).contains(entityB).doesNotContain(entityA);
        assertThat(search("userId", "987654321").get("content")).isEmpty();
    }

    @Test
    void dateFiltersAlone() throws Exception {
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        assertThat(search("from", past).get("content").findValuesAsText("entity")).contains(entityA, entityB);
        assertThat(search("to", future).get("content").findValuesAsText("entity")).contains(entityA, entityB);
        assertThat(search("from", future).get("content").findValuesAsText("entity"))
                .doesNotContain(entityA, entityB);
        assertThat(search("to", past).get("content").findValuesAsText("entity")).doesNotContain(entityA, entityB);
    }

    @Test
    void allFiltersTogether() throws Exception {
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        JsonNode page = search("userId", String.valueOf(USER_A), "entity", entityA, "action", "UPDATE",
                "from", past, "to", future);
        assertThat(actions(page)).containsExactly("UPDATE");
    }

    @Test
    void paginationAndOrdering() throws Exception {
        MvcResult result = mockMvc.perform(request("/api/audit-logs", "entity", entityA, "size", "1", "page", "0"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode page = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        // newest first: UPDATE was recorded after CREATE
        assertThat(page.get("content").get(0).get("action").asText()).isEqualTo("UPDATE");
    }

    @Test
    void csvExportWithAndWithoutFilters() throws Exception {
        MvcResult all = mockMvc.perform(request("/api/audit-logs/export")).andReturn();
        assertThat(all.getResponse().getStatus()).isEqualTo(200);
        assertThat(all.getResponse().getContentAsString()).contains(entityA, entityB);

        MvcResult filtered = mockMvc.perform(request("/api/audit-logs/export", "entity", entityB)).andReturn();
        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(filtered.getResponse().getContentAsString()).contains(entityB).doesNotContain(entityA);
    }
}
