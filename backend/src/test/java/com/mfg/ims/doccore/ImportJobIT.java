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
import org.springframework.mock.web.MockMultipartFile;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportJobIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Test#IMP2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAdminService userAdminService;
    @Autowired private JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @BeforeAll
    void setUp() throws Exception {
        String username = "it_imp_" + runId;
        var actor = new CachedUser(9920L, "it-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(username, null, "IT Import Admin",
                    username + "@it-imp.test", null, PASSWORD, Set.of("ADMIN")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE ims.users SET must_change_password = FALSE WHERE email LIKE ?", "%@it-imp.test");
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM ims.notifications WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-imp.test");
        jdbc.update("DELETE FROM ims.refresh_tokens WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-imp.test");
        jdbc.update("DELETE FROM ims.password_history WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-imp.test");
        jdbc.update("DELETE FROM ims.user_roles WHERE user_id IN (SELECT id FROM ims.users WHERE email LIKE ?)", "%@it-imp.test");
        jdbc.update("DELETE FROM ims.users WHERE email LIKE ?", "%@it-imp.test");
    }

    /** IMP-1: list returns import jobs */
    @Test
    void listImportJobs() throws Exception {
        mockMvc.perform(get("/api/import").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /** IMP-2: upload a CSV file creates a QUEUED import job */
    @Test
    void uploadCsvCreatesQueuedJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "materials.csv", "text/csv",
                "code,name,uom\nSTL-001,Steel Rod,KG\nSTL-002,Copper Rod,KG".getBytes());

        String resp = mockMvc.perform(multipart("/api/import")
                        .file(file)
                        .param("entityType", "MATERIAL")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.entityType").value("MATERIAL"))
                .andExpect(jsonPath("$.fileName").value("materials.csv"))
                .andReturn().getResponse().getContentAsString();

        Long jobId = mapper.readTree(resp).get("id").asLong();
        assertThat(jobId).isPositive();
    }

    /** IMP-3: cancel a QUEUED job transitions to CANCELLED */
    @Test
    void cancelQueuedJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.csv", "text/csv",
                "code,name\nPRD-001,Widget A".getBytes());

        String created = mockMvc.perform(multipart("/api/import")
                        .file(file)
                        .param("entityType", "PRODUCT")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long jobId = mapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/import/" + jobId + "/cancel")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /** IMP-4: cannot cancel an already-cancelled job */
    @Test
    void cannotCancelAlreadyCancelledJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "suppliers.csv", "text/csv",
                "code,name\nSUP-001,ACME".getBytes());

        String created = mockMvc.perform(multipart("/api/import")
                        .file(file)
                        .param("entityType", "SUPPLIER")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long jobId = mapper.readTree(created).get("id").asLong();

        // First cancel
        mockMvc.perform(post("/api/import/" + jobId + "/cancel")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        // Second cancel should fail with 422 / bad state
        mockMvc.perform(post("/api/import/" + jobId + "/cancel")
                        .header("Authorization", token))
                .andExpect(status().is4xxClientError());
    }
}
