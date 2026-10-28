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
class ItemCategoryIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Test#Cat2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAdminService userAdminService;
    @Autowired private JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @BeforeAll
    void setUp() throws Exception {
        String username = "it_cat_" + runId;
        var actor = new CachedUser(9901L, "it-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(username, null, "IT Category Admin",
                    username + "@it.test", null, PASSWORD, Set.of("ADMIN")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE ims.users SET must_change_password = FALSE WHERE email LIKE ?", "%@it.test");
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    @AfterAll
    void tearDown() {
        // Nullify FK references before deleting — parallel ITs (e.g. OperatorAssignmentIT,
        // HrmIT, SparePartAndMaintenanceCostIT) may link our ADMIN test user to production
        // operations, employees, or spare-part issue lines.
        jdbc.update("DELETE FROM ims.spare_part_issue_lines WHERE issued_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("UPDATE ims.employees SET user_id = NULL WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("UPDATE ims.production_operations SET assigned_operator_id = NULL WHERE assigned_operator_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("UPDATE ims.documents SET approved_by = NULL WHERE approved_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("UPDATE ims.ecos SET approved_by = NULL WHERE approved_by IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("DELETE FROM ims.notifications WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("DELETE FROM ims.refresh_tokens WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("DELETE FROM ims.password_history WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("DELETE FROM ims.user_roles WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it.test");
        jdbc.update("DELETE FROM ims.users WHERE email LIKE ?", "%@it.test");
    }

    /** IT-CAT-1: create a new item category */
    @Test
    void createItemCategory() throws Exception {
        String req = mapper.writeValueAsString(Map.of(
                "categoryCode", "CAT-" + runId.substring(0, 8),
                "name", "Test Category",
                "itemType", "BOTH"));

        mockMvc.perform(post("/api/admin/item-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Category"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /** IT-CAT-2: list returns active categories */
    @Test
    void listItemCategories() throws Exception {
        mockMvc.perform(get("/api/admin/item-categories").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /** IT-CAT-3: duplicate category code returns 409 */
    @Test
    void duplicateCategoryCodeReturns409() throws Exception {
        String code = "DUP-" + runId.substring(0, 8);
        String req = mapper.writeValueAsString(Map.of("categoryCode", code, "name", "First", "itemType", "BOTH"));

        mockMvc.perform(post("/api/admin/item-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/item-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isConflict());
    }
}
