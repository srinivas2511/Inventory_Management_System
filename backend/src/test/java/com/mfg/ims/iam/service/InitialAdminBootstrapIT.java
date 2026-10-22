package com.mfg.ims.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * First start of a fresh installation (production-style: no dev profile) against its own empty PostgreSQL, with the
 * bootstrap variables set. The shared test database already holds users, so it cannot show the "no users yet" case.
 */
@SpringBootTest(properties = {
        "ims.bootstrap.admin.username=boot-admin",
        "ims.bootstrap.admin.password=Str0ng#Bootstrap-Pw",
        "ims.bootstrap.admin.email=boot-admin@example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminBootstrapIT {

    private static final String PASSWORD = "Str0ng#Bootstrap-Pw";

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ims").withUsername("ims").withPassword("ims");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        synchronized (postgres) {
            if (!postgres.isRunning()) {
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialAdminBootstrap bootstrap;

    private long users() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ims.users", Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void theFirstStartCreatedTheAdministratorFromTheEnvironment() throws Exception {
        assertThat(users()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username FROM ims.users", String.class)).isEqualTo("boot-admin");
        assertThat(jdbc.queryForObject("""
                SELECT r.code FROM ims.users u JOIN ims.user_roles ur ON ur.user_id = u.id
                  JOIN ims.roles r ON r.id = ur.role_id
                """, String.class)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM ims.users", Boolean.class)).isTrue();

        // BCrypt with cost 12, never the plain password
        String hash = jdbc.queryForObject("SELECT password_hash FROM ims.users", String.class);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.audit_logs WHERE action = 'CREATE' AND entity = 'USER'
                   AND reason = 'Initial administrator created from the environment'
                """, Long.class)).isGreaterThanOrEqualTo(1);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"boot-admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void runningAgainWhenUsersExistCreatesNothing() {
        long before = users();
        assertThat(before).isGreaterThanOrEqualTo(1);

        bootstrap.onReady();
        bootstrap.onReady();

        assertThat(users()).isEqualTo(before);
    }

    @Test
    void nothingSecretIsLoggedWhileCreatingTheAdministrator(CapturedOutput output) {
        // empty the (isolated) user tables so the bootstrap runs its creation path inside this test
        jdbc.execute("TRUNCATE ims.users RESTART IDENTITY CASCADE");
        assertThat(users()).isZero();

        bootstrap.onReady();

        assertThat(users()).isEqualTo(1);
        assertThat(output.getAll()).contains("Initial administrator 'boot-admin' created from the environment");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("$2a$");
    }
}
