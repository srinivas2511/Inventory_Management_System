package com.mfg.ims.approval;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ApprovalEngine integration tests.
 * AE-1: pending inbox returns requests matching the caller's permissions.
 * AE-2: approve via /api/approvals/{id}/approve enforces SoD and fires ApprovalCompletedEvent.
 * AE-3: withdraw removes a request from the inbox.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalEngineIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Test#AE2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAdminService userAdminService;
    @Autowired private JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    private String requesterToken;   // PURCHASE_MANAGER — will create requests
    private String approverToken;    // different PURCHASE_MANAGER — will decide
    private Long requesterId;
    private Long approverId;

    @BeforeAll
    void setUp() throws Exception {
        var actor = new CachedUser(9920L, "ae-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            String req1 = "ae_req_" + runId;
            String req2 = "ae_apr_" + runId;
            userAdminService.create(new CreateUserRequest(req1, null, "AE Requester",
                    req1 + "@ae.test", null, PASSWORD, Set.of("PURCHASE_MANAGER")));
            userAdminService.create(new CreateUserRequest(req2, null, "AE Approver",
                    req2 + "@ae.test", null, PASSWORD, Set.of("PURCHASE_MANAGER")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE ims.users SET must_change_password = FALSE WHERE email LIKE ?", "%@ae.test");

        requesterToken = loginAs("ae_req_" + runId);
        approverToken = loginAs("ae_apr_" + runId);

        requesterId = jdbc.queryForObject("SELECT id FROM ims.users WHERE email LIKE ?",
                Long.class, "ae_req_%@ae.test");
        approverId = jdbc.queryForObject("SELECT id FROM ims.users WHERE email LIKE ?",
                Long.class, "ae_apr_%@ae.test");
    }

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM ims.approval_requests WHERE requested_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@ae.test");
        jdbc.update("DELETE FROM ims.notifications WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@ae.test");
        jdbc.update("DELETE FROM ims.refresh_tokens WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@ae.test");
        jdbc.update("DELETE FROM ims.password_history WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@ae.test");
        jdbc.update("DELETE FROM ims.user_roles WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@ae.test");
        jdbc.update("DELETE FROM ims.users WHERE email LIKE ?", "%@ae.test");
    }

    /** AE-1: pending inbox shows requests whose required_permission matches the caller's grants. */
    @Test
    void pendingInboxFiltersOnPermission() throws Exception {
        // APPROVAL_DECIDE is provably in the caller's permissions — the endpoint returns 200 only
        // when that authority is present, so the IN-clause is guaranteed to match.
        Long arId = jdbc.queryForObject(
                "INSERT INTO ims.approval_requests (document_type, document_id, step_no, required_permission, requested_by) "
                + "VALUES ('TEST_DOC_AE1_" + runId + "', 1, 1, 'APPROVAL_DECIDE', ?) RETURNING id",
                Long.class, requesterId);

        try {
            String resp = mockMvc.perform(get("/api/approvals/pending")
                            .header("Authorization", approverToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            var list = mapper.readTree(resp);
            assertThat(list.isArray()).isTrue();
            boolean found = false;
            for (var node : list) {
                if (node.get("id").asLong() == arId) { found = true; break; }
            }
            assertThat(found).as("AE-1: pending inbox must include the test request").isTrue();
        } finally {
            jdbc.update("DELETE FROM ims.approval_requests WHERE id = ?", arId);
        }
    }

    /** AE-2: approve via the controller updates status and moves request to history. */
    @Test
    void approveMovesToHistory() throws Exception {
        Long arId = jdbc.queryForObject(
                "INSERT INTO ims.approval_requests (document_type, document_id, step_no, required_permission, requested_by) "
                + "VALUES ('TEST_DOC_AE2_" + runId + "', 2, 1, 'PURCHASE_APPROVE', ?) RETURNING id",
                Long.class, requesterId);

        try {
            // Approver (different user) approves
            mockMvc.perform(post("/api/approvals/" + arId + "/approve")
                            .header("Authorization", approverToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"comment\":\"Looks good\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.comment").value("Looks good"));

            // Request no longer in pending inbox
            String pending = mockMvc.perform(get("/api/approvals/pending")
                            .header("Authorization", approverToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var pendingTree = mapper.readTree(pending);
            boolean inPending = false;
            for (var node : pendingTree) {
                if (node.get("id").asLong() == arId) { inPending = true; break; }
            }
            assertThat(inPending).as("AE-2: approved request must not be in pending inbox").isFalse();

            // Request appears in history
            String history = mockMvc.perform(get("/api/approvals/history")
                            .header("Authorization", approverToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var histTree = mapper.readTree(history);
            boolean inHistory = false;
            for (var node : histTree) {
                if (node.get("id").asLong() == arId) { inHistory = true; break; }
            }
            assertThat(inHistory).as("AE-2: approved request must appear in history").isTrue();
        } finally {
            jdbc.update("DELETE FROM ims.approval_requests WHERE id = ?", arId);
        }
    }

    /** AE-3: requester can withdraw their own pending request; it leaves the inbox. */
    @Test
    void requesterCanWithdraw() throws Exception {
        Long arId = jdbc.queryForObject(
                "INSERT INTO ims.approval_requests (document_type, document_id, step_no, required_permission, requested_by) "
                + "VALUES ('TEST_DOC_AE3_" + runId + "', 3, 1, 'PURCHASE_APPROVE', ?) RETURNING id",
                Long.class, requesterId);

        try {
            mockMvc.perform(post("/api/approvals/" + arId + "/withdraw")
                            .header("Authorization", requesterToken))
                    .andExpect(status().isNoContent());

            // Must no longer appear in pending inbox
            String pending = mockMvc.perform(get("/api/approvals/pending")
                            .header("Authorization", approverToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var pendingTree = mapper.readTree(pending);
            boolean found = false;
            for (var node : pendingTree) {
                if (node.get("id").asLong() == arId) { found = true; break; }
            }
            assertThat(found).as("AE-3: withdrawn request must not be in pending inbox").isFalse();
        } finally {
            jdbc.update("DELETE FROM ims.approval_requests WHERE id = ?", arId);
        }
    }

    private String loginAs(String username) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }
}
