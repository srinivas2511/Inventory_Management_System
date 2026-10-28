package com.mfg.ims.doccore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalRuleIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Test#AR2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAdminService userAdminService;
    @Autowired private JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @BeforeAll
    void setUp() throws Exception {
        String username = "it_ar_" + runId;
        var actor = new CachedUser(9910L, "it-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(username, null, "IT Approval Admin",
                    username + "@it-ar.test", null, PASSWORD, Set.of("ADMIN")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE ims.users SET must_change_password = FALSE WHERE email LIKE ?", "%@it-ar.test");
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM ims.notifications WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-ar.test");
        jdbc.update("DELETE FROM ims.refresh_tokens WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-ar.test");
        jdbc.update("DELETE FROM ims.password_history WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-ar.test");
        jdbc.update("DELETE FROM ims.user_roles WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-ar.test");
        jdbc.update("DELETE FROM ims.users WHERE email LIKE ?", "%@it-ar.test");
    }

    /** AR-1: create an approval rule with two steps */
    @Test
    void createApprovalRuleWithSteps() throws Exception {
        String req = mapper.writeValueAsString(Map.of(
                "documentType", "PURCHASE_ORDER",
                "ruleName", "PO Approval " + runId,
                "priority", 10,
                "steps", List.of(
                        Map.of("stepNo", 1, "stepGroup", 1, "approverType", "ROLE",
                                "approverRef", "FINANCE_MANAGER", "requiredPermission", "PURCHASE_APPROVE"),
                        Map.of("stepNo", 2, "stepGroup", 2, "approverType", "ROLE",
                                "approverRef", "ADMIN", "requiredPermission", "PURCHASE_APPROVE")
                )
        ));

        mockMvc.perform(post("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("PURCHASE_ORDER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].approverType").value("ROLE"));
    }

    /** AR-2: update replaces all steps */
    @Test
    void updateReplacesSteps() throws Exception {
        String createReq = mapper.writeValueAsString(Map.of(
                "documentType", "SALES_ORDER",
                "ruleName", "SO Rule " + runId,
                "priority", 5,
                "steps", List.of(
                        Map.of("stepNo", 1, "stepGroup", 1, "approverType", "ROLE",
                                "approverRef", "SALES_MANAGER", "requiredPermission", "SALES_APPROVE")
                )
        ));

        String created = mockMvc.perform(post("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createReq))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = mapper.readTree(created).get("id").asLong();

        // Update: replace 1 step with 2 steps
        String updateReq = mapper.writeValueAsString(Map.of(
                "documentType", "SALES_ORDER",
                "ruleName", "SO Rule Updated " + runId,
                "priority", 5,
                "steps", List.of(
                        Map.of("stepNo", 1, "stepGroup", 1, "approverType", "ROLE",
                                "approverRef", "SALES_MANAGER", "requiredPermission", "SALES_APPROVE"),
                        Map.of("stepNo", 2, "stepGroup", 2, "approverType", "ROLE",
                                "approverRef", "ADMIN", "requiredPermission", "SALES_APPROVE")
                )
        ));

        mockMvc.perform(put("/api/admin/approval-rules/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(updateReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleName").value("SO Rule Updated " + runId))
                .andExpect(jsonPath("$.steps.length()").value(2));
    }

    /** AR-3: deactivate sets active=false */
    @Test
    void deactivateRule() throws Exception {
        String createReq = mapper.writeValueAsString(Map.of(
                "documentType", "JOURNAL",
                "ruleName", "Journal Rule " + runId,
                "priority", 1,
                "steps", List.of(
                        Map.of("stepNo", 1, "stepGroup", 1, "approverType", "ROLE",
                                "approverRef", "FINANCE_MANAGER", "requiredPermission", "JOURNAL_APPROVE")
                )
        ));

        String created = mockMvc.perform(post("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createReq))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = mapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/admin/approval-rules/" + id)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // Deactivated rule is not returned in the active list for that document type
        mockMvc.perform(get("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .param("documentType", "JOURNAL"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assert !body.contains("Journal Rule " + runId) : "Deactivated rule should not appear in active list";
                });
    }

    /** AR-4: list filtered by document type returns only matching rules */
    @Test
    void listFilteredByDocumentType() throws Exception {
        String docType = "GOODS_RECEIPT_" + runId.substring(0, 6).toUpperCase();
        String createReq = mapper.writeValueAsString(Map.of(
                "documentType", docType,
                "ruleName", "GR Rule " + runId,
                "priority", 0,
                "steps", List.of(
                        Map.of("stepNo", 1, "stepGroup", 1, "approverType", "ROLE",
                                "approverRef", "PURCHASE_MANAGER", "requiredPermission", "GOODS_RECEIVE")
                )
        ));

        mockMvc.perform(post("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createReq))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/approval-rules")
                        .header("Authorization", token)
                        .param("documentType", docType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentType").value(docType));
    }
}
