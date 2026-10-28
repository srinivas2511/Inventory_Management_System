package com.mfg.ims.doccore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.doccore.docseries.DocumentSeriesService;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentSeriesIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Test#Ser2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private DocumentSeriesService seriesService;
    @Autowired private UserAdminService userAdminService;
    @Autowired private JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @BeforeAll
    void setUp() throws Exception {
        String username = "it_ser_" + runId;
        var actor = new CachedUser(9902L, "it-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(username, null, "IT Series Admin",
                    username + "@itseries.test", null, PASSWORD, Set.of("ADMIN")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE ims.users SET must_change_password = FALSE WHERE email LIKE ?", "%@itseries.test");
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    @AfterAll
    void tearDown() {
        jdbc.update("UPDATE ims.employees SET user_id = NULL WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("UPDATE ims.production_operations SET assigned_operator_id = NULL WHERE assigned_operator_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("UPDATE ims.documents SET approved_by = NULL WHERE approved_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("UPDATE ims.ecos SET approved_by = NULL WHERE approved_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("DELETE FROM ims.notifications WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("DELETE FROM ims.refresh_tokens WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("DELETE FROM ims.password_history WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("DELETE FROM ims.user_roles WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@itseries.test");
        jdbc.update("DELETE FROM ims.users WHERE email LIKE ?", "%@itseries.test");
    }

    /** DS-1: create a new document series via REST */
    @Test
    void createDocumentSeries() throws Exception {
        String seriesCode = "TD-001-" + runId.substring(0, 6);
        String req = mapper.writeValueAsString(Map.of(
                "documentKind", "TEST_DOC_" + runId.substring(0, 8),
                "seriesCode", seriesCode,
                "format", "TD-{FY}-{00000}",
                "numbering", "AUTO",
                "resetPolicy", "FY",
                "isDefault", true,
                "statutory", false));

        mockMvc.perform(post("/api/admin/document-series")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seriesCode").value(seriesCode.toUpperCase()))
                .andExpect(jsonPath("$.active").value(true));
    }

    /** DS-2: nextNumber generates a formatted number */
    @Test
    void nextNumberGeneratesFormattedNumber() {
        LocalDate date = LocalDate.of(2026, 11, 5);
        String num = seriesService.nextNumber(1L, "SALES_INVOICE", date);
        assertThat(num).startsWith("INV-2026-27-");
        assertThat(num).matches("INV-2026-27-\\d{5}");
    }

    /** DS-3: FY rollover creates a new counter (two FY period calls) */
    @Test
    void fyRolloverCreatesNewCounter() {
        LocalDate fy1 = LocalDate.of(2026, 4, 1);
        LocalDate fy2 = LocalDate.of(2027, 4, 1);
        String n1 = seriesService.nextNumber(1L, "RECEIPT", fy1);
        String n2 = seriesService.nextNumber(1L, "RECEIPT", fy2);
        assertThat(n1).endsWith("00001");
        assertThat(n2).endsWith("00001");
    }

    /** DS-4: list returns seeded series */
    @Test
    void listReturnsSeries() throws Exception {
        mockMvc.perform(get("/api/admin/document-series")
                        .header("Authorization", token)
                        .param("documentKind", "SALES_INVOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].seriesCode").value("INV-DEFAULT"));
    }
}
