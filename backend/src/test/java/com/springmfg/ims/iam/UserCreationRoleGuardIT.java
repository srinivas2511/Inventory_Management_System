package com.springmfg.ims.iam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Holding USER_CREATE must not be a way to hand out roles, and so permissions: assigning roles at creation needs
 * ROLE_MANAGE as well. The callers here hold hand-built roles made of seeded permissions and sign in through the
 * real login, so the JWT filter resolves what the database says they hold.
 */
class UserCreationRoleGuardIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Guard#Check-2026";
    private static final String NEW_USER_PASSWORD = "Fresh#Start-2026x";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);
    private static final String CREATE_ONLY = "RCG_CREATE_ONLY";
    private static final String CREATE_AND_ROLES = "RCG_CREATE_ROLES";

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void roles() {
        cleanUp();
        role(CREATE_ONLY, "USER_CREATE");
        role(CREATE_AND_ROLES, "USER_CREATE", "ROLE_MANAGE");
    }

    @AfterEach
    void cleanUp() {
        String like = "roleguard-%";
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code LIKE 'RCG_%')");
        jdbc.update("DELETE FROM roles WHERE code LIKE 'RCG_%'");
    }

    private void role(String code, String... permissions) {
        jdbc.update("INSERT INTO roles (code, name) VALUES (?, ?)", code, code);
        for (String permission : permissions) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) "
                    + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?", code, permission);
        }
    }

    private String callerToken(String roleCode) throws Exception {
        String username = "roleguard-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Guard caller", username + "@guard.test", null,
                PASSWORD, Set.of(roleCode)));
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String newUserBody(String rolesJson, String password) {
        String username = "roleguard-new-" + SEQ.incrementAndGet();
        return "{\"username\":\"" + username + "\",\"fullName\":\"New Person\",\"email\":\"" + username
                + "@guard.test\",\"password\":\"" + password + "\"" + rolesJson + "}";
    }

    @Test
    void userCreateAloneCannotHandOutRoles() throws Exception {
        String token = callerToken(CREATE_ONLY);

        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody(",\"roleCodes\":[\"ADMIN\"]", NEW_USER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCreateAloneMayStillCreateAUserWithNoRoles() throws Exception {
        String token = callerToken(CREATE_ONLY);

        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(newUserBody("", NEW_USER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.roles").isEmpty());

        // An explicitly empty list is no role either.
        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody(",\"roleCodes\":[]", NEW_USER_PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void roleManageHolderMayAssignRolesAtCreation() throws Exception {
        String token = callerToken(CREATE_AND_ROLES);

        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody(",\"roleCodes\":[\"OPERATOR\"]", NEW_USER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
    }

    @Test
    void anUnknownRoleCodeIsRejectedInsteadOfSilentlyDropped() throws Exception {
        String token = callerToken(CREATE_AND_ROLES);

        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody(",\"roleCodes\":[\"NO_SUCH_ROLE\"]", NEW_USER_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theInitialPasswordMustMeetThePasswordPolicy() throws Exception {
        String token = callerToken(CREATE_ONLY);

        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(newUserBody("", "alllowercase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
    }
}
