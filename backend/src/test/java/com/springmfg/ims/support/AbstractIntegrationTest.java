package com.springmfg.ims.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests: full application context against a <b>real PostgreSQL 16</b> started by
 * Testcontainers (never H2 - see ADR-12). One container is shared by all test classes in the JVM.
 * Skipped automatically when Docker is not available.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} is the correct mechanism here. JUnit 5's
 * {@code @EnabledIf} is evaluated per-method <em>after</em> {@code prepare()} fires, so Spring's context
 * loading runs before the condition is checked. The Testcontainers extension evaluates Docker availability
 * as a class-level {@code ExecutionCondition} and also starts {@code @Container} static fields in
 * {@code BeforeAllCallback} — both of which happen before any method {@code prepare()} runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ims")
            .withUsername("ims")
            .withPassword("ims");

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
