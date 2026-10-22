package com.mfg.ims.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * R9-7.10: a module an administrator switches off is refused by the API with MODULE_INACTIVE; the core is never gated.
 * Real login and HTTP, not {@code @Transactional}: the state is read from the database by the interceptor.
 */
class ModuleActivationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Temp#Start-2026";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    @Autowired UserAdminService userAdminService;
    @Autowired ModuleService modules;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void everythingActiveAgain() {
        modules.list().stream().filter(m -> !m.active()).forEach(m -> modules.setActive(m.code(), true));
        String like = "modit-%";
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    /** A signed-in user of the given role, with the temporary-password flag already cleared. */
    private String tokenFor(String role) throws Exception {
        String username = "modit-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Module Test", username + "@mod.test", null,
                PASSWORD, Set.of(role)));
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    @DisplayName("MOD-1: every optional module starts active and any signed-in user can read the list")
    void everythingStartsActive() throws Exception {
        String sales = tokenFor("SALES");

        mockMvc.perform(get("/api/modules").header("Authorization", sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[?(@.active == false)]").isEmpty());
    }

    @Test
    @DisplayName("MOD-2: switching CRM off refuses its API with MODULE_INACTIVE while the core keeps working; switching it on restores it")
    void aDeactivatedModuleIsRefusedAndTheCoreIsNot() throws Exception {
        String admin = tokenFor("ADMIN");
        mockMvc.perform(get("/api/crm/leads").header("Authorization", admin)).andExpect(status().isOk());

        mockMvc.perform(put("/api/modules/CRM").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/crm/leads").header("Authorization", admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_INACTIVE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("crm module is not active")));
        mockMvc.perform(get("/api/crm/quotations").header("Authorization", admin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/hrm/employees").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/modules").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.code == 'CRM')].active").value(false));

        mockMvc.perform(put("/api/modules/CRM").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/crm/leads").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MOD-3: only MODULE_MANAGE can switch a module, an unknown module is 404, and each change is audited")
    void onlyAnAdministratorSwitchesModulesAndItIsAudited() throws Exception {
        String sales = tokenFor("SALES");
        String admin = tokenFor("ADMIN");

        mockMvc.perform(put("/api/modules/CRM").header("Authorization", sales)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/modules/NOPE").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/modules/WMS").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ims.audit_logs
                 WHERE action = 'MODULE_ACTIVATION' AND entity_id = 'WMS' AND new_value::text LIKE '%INACTIVE%'
                """, Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("MOD-4: a path belongs to the module with the longest matching prefix; core paths belong to none")
    void pathsMapToModules() {
        assertThat(ModuleService.moduleOf("/api/ar/credit-notes")).isEqualTo("FINANCE");
        assertThat(ModuleService.moduleOf("/api/config/tax-codes/all")).isEqualTo("FINANCE");
        assertThat(ModuleService.moduleOf("/api/ap")).isEqualTo("FINANCE");
        assertThat(ModuleService.moduleOf("/api/hrm/payroll/runs")).isEqualTo("HRM");
        assertThat(ModuleService.moduleOf("/api/ecos/5")).isEqualTo("PLM");
        assertThat(ModuleService.moduleOf("/api/spc/charts")).isEqualTo("QUALITY_PLUS");
        assertThat(ModuleService.moduleOf("/api/apple")).as("a prefix must end at a path segment").isNull();
        assertThat(ModuleService.moduleOf("/api/customers")).isNull();
        assertThat(ModuleService.moduleOf("/api/production-orders")).isNull();
        assertThat(ModuleService.moduleOf("/api/modules")).isNull();
        assertThat(ModuleService.moduleOf("/api/config/settings")).isNull();
    }
}
