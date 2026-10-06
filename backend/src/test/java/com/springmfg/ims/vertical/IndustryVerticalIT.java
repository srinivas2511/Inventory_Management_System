package com.springmfg.ims.vertical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.config.VerticalFlywayConfig;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * R20 Gate M20 against real PostgreSQL: industry verticals are opt-in data packs, product types are data, attribute
 * definitions are managed through an API, and a brand-new vertical needs no code change.
 *
 * <p>The Flyway tests build throw-away databases in the shared container, so the shared database is never given a
 * vertical by migration. The API tests are transactional and roll back everything they insert.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndustryVerticalIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Vertical#Test-2026";
    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "OPERATOR");
    private static final String SEEDED_OPERATIONS = "operation_code ~ '^(AUT|FUR|ELE)-'";
    private static final String SEEDED_REASONS = "reason_code ~ '^(AUT|FUR|ELE)-'";

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired CacheManager cacheManager;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String dbUser;
    @Value("${spring.datasource.password}") String dbPassword;

    private final Map<String, String> tokens = new LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createUsersAndLogIn() throws Exception {
        var actor = new CachedUser(9003L, "vertical-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            for (String role : ROLES) {
                String username = username(role);
                userAdminService.create(new CreateUserRequest(username, null, "VERT " + role,
                        username + "@vertical.test", null, PASSWORD, Set.of(role)));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        for (String role : ROLES) {
            MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn();
            assertThat(login.getResponse().getStatus()).as("login as %s", role).isEqualTo(200);
            tokens.put(role, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
        }
    }

    @AfterAll
    void removeTheUsers() {
        String like = "vt_%_" + runId;
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
    }

    @AfterEach
    void clearAttributeCache() {
        var cache = cacheManager.getCache("springAttributes");
        if (cache != null) {
            cache.clear();
        }
    }

    private String username(String role) {
        return "vt_" + role.toLowerCase() + "_" + runId;
    }

    // ---- Flyway: opt-in packs on throw-away databases --------------------------------------------------------------

    @Test
    @DisplayName("A default deployment has no vertical data, only the seven spring product types")
    void defaultDeploymentHasNoVerticalData() throws Exception {
        assertNoVerticalData(jdbc, "the shared test database");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_types", Long.class)).isEqualTo(7L);

        String db = createDatabase();
        try {
            flyway(db, "").load().migrate();
            JdbcTemplate fresh = jdbcFor(db);
            assertNoVerticalData(fresh, "a fresh default database");
            assertThat(fresh.queryForObject("SELECT count(*) FROM ims.product_types", Long.class)).isEqualTo(7L);
        } finally {
            dropDatabase(db);
        }
    }

    @Test
    @DisplayName("Enabling a vertical applies exactly that vertical, later enabling adds more, re-running is a no-op")
    void enablingAVerticalAppliesIt() throws Exception {
        String db = createDatabase();
        try {
            flyway(db, "").load().migrate();
            JdbcTemplate t = jdbcFor(db);
            assertNoVerticalData(t, "before any vertical is enabled");

            flyway(db, "automotive").load().migrate();
            assertThat(count(t, "ims.operations", "operation_code LIKE 'AUT-%'")).isEqualTo(6);
            assertThat(count(t, "ims.rejection_reasons", "reason_code LIKE 'AUT-%'")).isEqualTo(4);
            assertThat(count(t, "ims.product_types", "code = 'AUTOMOTIVE_PART' AND industry = 'AUTOMOTIVE'"))
                    .isEqualTo(1);
            assertThat(count(t, "ims.spring_attribute_definitions", "spring_type = 'AUTOMOTIVE_PART'")).isEqualTo(5);
            assertThat(count(t, "ims.operations", "operation_code ~ '^(FUR|ELE)-'")).isZero();
            assertThat(count(t, "ims.product_types", "code IN ('FURNITURE_ITEM','ELECTRONIC_COMPONENT')")).isZero();

            var rerun = flyway(db, "automotive").load().migrate();
            assertThat(rerun.migrationsExecuted).as("re-running with nothing new").isZero();

            flyway(db, "automotive,furniture,electronics").load().migrate();
            assertThat(count(t, "ims.operations", SEEDED_OPERATIONS)).isEqualTo(16);
            assertThat(count(t, "ims.rejection_reasons", SEEDED_REASONS)).isEqualTo(12);
            assertThat(count(t, "ims.product_types", "industry <> 'SPRING'")).isEqualTo(3);
            assertThat(count(t, "ims.spring_attribute_definitions",
                    "spring_type IN ('FURNITURE_ITEM','ELECTRONIC_COMPONENT')")).isEqualTo(8);
            assertThat(count(t, "ims.spring_attribute_definitions",
                    "spring_type = 'FURNITURE_ITEM' AND data_type = 'ENUM' AND enum_values IS NOT NULL")).isEqualTo(2);
            assertThat(count(t, "ims.product_types", "industry = 'SPRING'")).as("spring types untouched").isEqualTo(7);
        } finally {
            dropDatabase(db);
        }
    }

    @Test
    @DisplayName("Switching a vertical off again does not stop the application starting; its data stays")
    void disabledVerticalDoesNotBreakStartup() throws Exception {
        String db = createDatabase();
        try {
            flyway(db, "automotive").load().migrate();
            JdbcTemplate t = jdbcFor(db);
            assertThat(count(t, "ims.operations", "operation_code LIKE 'AUT-%'")).isEqualTo(6);

            Flyway withoutIt = flyway(db, "").load();
            withoutIt.validate();
            withoutIt.migrate();
            assertThat(count(t, "ims.operations", "operation_code LIKE 'AUT-%'")).as("data stays").isEqualTo(6);
        } finally {
            dropDatabase(db);
        }
    }

    // ---- API: product types are data -------------------------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("Spring products are unchanged; a vertical type is refused until its pack is applied")
    void springProductsUnchangedAndVerticalTypeRefusedUntilEnabled() throws Exception {
        for (String type : List.of("COMPRESSION", "BELLEVILLE", "WIRE_FORM")) {
            MvcResult created = createProduct("ENGINEER", type, null);
            assertThat(created.getResponse().getStatus()).as("create %s", type).isEqualTo(201);
        }

        MvcResult refused = createProduct("ENGINEER", "AUTOMOTIVE_PART", null);
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(refused.getResponse().getContentAsString()).contains("springType");

        MvcResult nonsense = createProduct("ENGINEER", "NOT_A_TYPE", null);
        assertThat(nonsense.getResponse().getStatus()).isEqualTo(400);

        MvcResult types = call("ENGINEER", get("/api/product-types"));
        assertThat(types.getResponse().getStatus()).isEqualTo(200);
        List<String> codes = JsonPath.read(types.getResponse().getContentAsString(), "$[*].code");
        assertThat(codes).containsExactly("COMPRESSION", "EXTENSION", "TORSION", "CONICAL", "BELLEVILLE",
                "WIRE_FORM", "CUSTOM");
    }

    @Test
    @Transactional
    @DisplayName("Gate M20: each shipped vertical lets a product of its type be created with its attributes")
    void eachShippedVerticalWorksEndToEnd() throws Exception {
        applyVertical("automotive");
        applyVertical("furniture");
        applyVertical("electronics");

        MvcResult types = call("ENGINEER", get("/api/product-types"));
        List<String> codes = JsonPath.read(types.getResponse().getContentAsString(), "$[*].code");
        assertThat(codes).contains("AUTOMOTIVE_PART", "FURNITURE_ITEM", "ELECTRONIC_COMPONENT");

        assertThat(createProduct("ENGINEER", "AUTOMOTIVE_PART",
                "{\"thread_pitch\":1.5,\"tensile_strength\":900,\"hardness\":38}").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createProduct("ENGINEER", "FURNITURE_ITEM",
                "{\"load_capacity_kg\":120,\"finish_type\":\"MATTE\",\"wood_grade\":\"A\"}").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createProduct("ENGINEER", "ELECTRONIC_COMPONENT",
                "{\"component_type\":\"CAPACITOR\",\"tolerance_mm\":0.05}").getResponse().getStatus())
                .isEqualTo(201);

        for (String type : List.of("AUTOMOTIVE_PART", "FURNITURE_ITEM", "ELECTRONIC_COMPONENT")) {
            MvcResult attrs = call("ENGINEER", get("/api/products/attributes/" + type));
            assertThat(attrs.getResponse().getStatus()).as("attributes of %s", type).isEqualTo(200);
            List<String> attrCodes = JsonPath.read(attrs.getResponse().getContentAsString(), "$[*].attributeCode");
            assertThat(attrCodes).as("attributes of %s", type).isNotEmpty();
        }

        assertThat(count(jdbc, "operations", SEEDED_OPERATIONS)).isEqualTo(16);
        assertThat(count(jdbc, "rejection_reasons", SEEDED_REASONS)).isEqualTo(12);
    }

    @Test
    @Transactional
    @DisplayName("Gate M20: a brand-new vertical is data only - no Java change - and inactive types are refused")
    void aNewVerticalNeedsNoCodeChange() throws Exception {
        String newVertical = """
                INSERT INTO product_types (code, name, industry, display_order)
                VALUES ('MEDICAL_DEVICE', 'Medical device', 'MEDICAL', 50);
                INSERT INTO spring_attribute_definitions
                  (spring_type, attribute_code, label, data_type, required, enum_values, display_order)
                VALUES ('MEDICAL_DEVICE', 'sterility_class', 'Sterility Class', 'ENUM', true,
                        '["STERILE","NON_STERILE"]'::jsonb, 1);
                """;
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ByteArrayResource(newVertical.getBytes()));

        assertThat(createProduct("ENGINEER", "MEDICAL_DEVICE", "{\"sterility_class\":\"STERILE\"}").getResponse()
                .getStatus()).isEqualTo(201);
        MvcResult attrs = call("OPERATOR", get("/api/products/attributes/MEDICAL_DEVICE"));
        assertThat(attrs.getResponse().getStatus()).isEqualTo(200);
        assertThat(attrs.getResponse().getContentAsString()).contains("sterility_class");

        jdbc.update("UPDATE product_types SET active = FALSE WHERE code = 'MEDICAL_DEVICE'");
        MvcResult inactive = createProduct("ENGINEER", "MEDICAL_DEVICE", null);
        assertThat(inactive.getResponse().getStatus()).as("inactive product type").isEqualTo(400);
        List<String> codes = JsonPath.read(call("ENGINEER", get("/api/product-types")).getResponse()
                .getContentAsString(), "$[*].code");
        assertThat(codes).doesNotContain("MEDICAL_DEVICE");
    }

    // ---- API: attribute definitions admin (R20-4.1) ----------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("Admin adds, edits, deactivates and re-activates attribute definitions without a migration")
    void adminManagesAttributeDefinitions() throws Exception {
        applyVertical("automotive");

        MvcResult created = call("ADMIN", post("/api/admin/attribute-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(definition("AUTOMOTIVE_PART", "coating_thickness", "Coating Thickness", "NUMBER", "um",
                        "null", "0", "50", 9)));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        int id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat((Boolean) JsonPath.read(created.getResponse().getContentAsString(), "$.active")).isTrue();

        List<String> listed = JsonPath.read(call("ADMIN", get("/api/admin/attribute-definitions")
                .param("productType", "AUTOMOTIVE_PART")).getResponse().getContentAsString(), "$[*].attributeCode");
        assertThat(listed).contains("coating_thickness", "thread_pitch");
        assertThat(attributeCodes("AUTOMOTIVE_PART")).hasSize(6).contains("coating_thickness");

        MvcResult updated = call("ADMIN", put("/api/admin/attribute-definitions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(definition("AUTOMOTIVE_PART", "coating_thickness", "Plating Thickness", "NUMBER", "um",
                        "null", "0", "75", 9)));
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(updated.getResponse().getContentAsString(), "$.label"))
                .isEqualTo("Plating Thickness");

        MvcResult renamed = call("ADMIN", put("/api/admin/attribute-definitions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(definition("AUTOMOTIVE_PART", "other_code", "X", "NUMBER", "um", "null", "0", "75", 9)));
        assertThat(renamed.getResponse().getStatus()).as("an attribute code never changes").isEqualTo(400);

        MvcResult off = call("ADMIN", post("/api/admin/attribute-definitions/{id}/deactivate", id));
        assertThat(off.getResponse().getStatus()).isEqualTo(200);
        assertThat(attributeCodes("AUTOMOTIVE_PART")).as("a deactivated attribute leaves the product form")
                .hasSize(5).doesNotContain("coating_thickness");

        MvcResult on = call("ADMIN", post("/api/admin/attribute-definitions/{id}/activate", id));
        assertThat(on.getResponse().getStatus()).isEqualTo(200);
        assertThat(attributeCodes("AUTOMOTIVE_PART")).hasSize(6).contains("coating_thickness");

        assertThat(count(jdbc, "audit_logs", "action = 'ATTRIBUTE_DEFINITION_CREATE'")).isGreaterThanOrEqualTo(1);
        assertThat(count(jdbc, "audit_logs", "action = 'ATTRIBUTE_DEFINITION_UPDATE'")).isGreaterThanOrEqualTo(1);
        assertThat(count(jdbc, "audit_logs", "action = 'ATTRIBUTE_DEFINITION_DEACTIVATE'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("Attribute definitions: ENUM values, limits, duplicates and unknown types are validated")
    void attributeDefinitionValidation() throws Exception {
        applyVertical("furniture");

        MvcResult enumDef = call("ADMIN", post("/api/admin/attribute-definitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(definition("FURNITURE_ITEM", "upholstery", "Upholstery", "ENUM", null,
                        "[\"LEATHER\",\"FABRIC\"]", "null", "null", 5)));
        assertThat(enumDef.getResponse().getStatus()).isEqualTo(201);
        assertThat((List<String>) JsonPath.read(enumDef.getResponse().getContentAsString(), "$.enumValues"))
                .containsExactly("LEATHER", "FABRIC");
        assertThat(call("ENGINEER", get("/api/products/attributes/FURNITURE_ITEM")).getResponse()
                .getContentAsString()).contains("upholstery");

        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "upholstery", "Again", "ENUM", null,
                "[\"A\"]", "null", "null", 5))).as("duplicate").isEqualTo(409);
        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "no_values", "No values", "ENUM", null,
                "[]", "null", "null", 5))).as("ENUM without values").isEqualTo(400);
        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "text_values", "Text values", "TEXT", null,
                "[\"A\"]", "null", "null", 5))).as("values on a non-ENUM").isEqualTo(400);
        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "bad_range", "Bad range", "NUMBER", "mm",
                "null", "10", "1", 5))).as("minimum above maximum").isEqualTo(400);
        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "limits_on_text", "Limits", "TEXT", null,
                "null", "1", "5", 5))).as("limits on a non-number").isEqualTo(400);
        assertThat(status("ADMIN", definition("NO_SUCH_TYPE", "anything", "Anything", "TEXT", null,
                "null", "null", "null", 5))).as("unknown product type").isEqualTo(400);
        assertThat(status("ADMIN", definition("FURNITURE_ITEM", "Bad-Code", "Bad code", "TEXT", null,
                "null", "null", "null", 5))).as("code format").isEqualTo(400);
    }

    @Test
    @DisplayName("Only ATTRIBUTE_MANAGE (ADMIN) reaches the admin endpoints; anyone with PRODUCT_VIEW reads the types")
    void permissions() throws Exception {
        String body = definition("COMPRESSION", "extra_attr", "Extra", "TEXT", null, "null", "null", "null", 1);
        assertThat(call("ENGINEER", get("/api/admin/attribute-definitions")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("OPERATOR", get("/api/admin/attribute-definitions")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("ENGINEER", post("/api/admin/attribute-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(body)).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("OPERATOR", get("/api/product-types")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("ENGINEER", get("/api/product-types")).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("ADMIN", get("/api/admin/attribute-definitions")).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private MvcResult createProduct(String role, String type, String attributesJson) throws Exception {
        String code = ("R20-" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
        String body = "{\"productCode\":\"" + code + "\",\"name\":\"R20 " + type + "\",\"springType\":\"" + type + "\""
                + (attributesJson == null ? "" : ",\"attributes\":" + attributesJson) + "}";
        return call(role, post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<String> attributeCodes(String type) throws Exception {
        MvcResult r = call("ENGINEER", get("/api/products/attributes/" + type));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(r.getResponse().getContentAsString(), "$[*].attributeCode");
    }

    private int status(String role, String body) throws Exception {
        return call(role, post("/api/admin/attribute-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(body)).getResponse().getStatus();
    }

    private static String definition(String type, String code, String label, String dataType, String unit,
            String enumValues, String min, String max, int order) {
        return "{\"productType\":\"" + type + "\",\"attributeCode\":\"" + code + "\",\"label\":\"" + label
                + "\",\"dataType\":\"" + dataType + "\"," + (unit == null ? "" : "\"unit\":\"" + unit + "\",")
                + "\"required\":false,\"minValue\":" + min + ",\"maxValue\":" + max + ",\"enumValues\":" + enumValues
                + ",\"displayOrder\":" + order + "}";
    }

    private MvcResult call(String role, MockHttpServletRequestBuilder b) throws Exception {
        return mockMvc.perform(b.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role))).andReturn();
    }

    /** Applies a shipped vertical's SQL to the transaction-bound connection, exactly as Flyway would run it. */
    private void applyVertical(String name) throws Exception {
        Resource[] scripts = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/vertical/" + name + "/*.sql");
        assertThat(scripts).as("scripts of vertical %s", name).isNotEmpty();
        Arrays.sort(scripts, (a, b) -> String.valueOf(a.getFilename()).compareTo(String.valueOf(b.getFilename())));
        for (Resource script : scripts) {
            ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                    new ClassPathResource("db/vertical/" + name + "/" + script.getFilename()));
        }
    }

    private void assertNoVerticalData(JdbcTemplate t, String where) {
        String ops = t.queryForObject("SELECT count(*) FROM ims.operations WHERE " + SEEDED_OPERATIONS, String.class);
        String reasons = t.queryForObject("SELECT count(*) FROM ims.rejection_reasons WHERE " + SEEDED_REASONS,
                String.class);
        String types = t.queryForObject("SELECT count(*) FROM ims.product_types WHERE industry <> 'SPRING'",
                String.class);
        assertThat(ops).as("vertical operations in %s", where).isEqualTo("0");
        assertThat(reasons).as("vertical rejection reasons in %s", where).isEqualTo("0");
        assertThat(types).as("non-spring product types in %s", where).isEqualTo("0");
    }

    private static long count(JdbcTemplate t, String table, String where) {
        return t.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Long.class);
    }

    private FluentConfiguration flyway(String db, String verticals) {
        FluentConfiguration cfg = Flyway.configure()
                .dataSource(urlFor(db), dbUser, dbPassword)
                .schemas("ims").defaultSchema("ims").createSchemas(true)
                .locations("classpath:db/migration");
        new VerticalFlywayConfig().verticalPackLocations(verticals).customize(cfg);
        return cfg;
    }

    private String urlFor(String db) {
        int q = url.indexOf('?');
        String head = q < 0 ? url : url.substring(0, q);
        String tail = q < 0 ? "" : url.substring(q);
        return head.substring(0, head.lastIndexOf('/') + 1) + db + tail;
    }

    private JdbcTemplate jdbcFor(String db) {
        return new JdbcTemplate(new DriverManagerDataSource(urlFor(db), dbUser, dbPassword));
    }

    private String createDatabase() throws Exception {
        String db = "vertical_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + db);
        }
        return db;
    }

    private void dropDatabase(String db) throws Exception {
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPassword); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }
}
