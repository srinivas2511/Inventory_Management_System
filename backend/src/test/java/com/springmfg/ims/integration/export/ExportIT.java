package com.springmfg.ims.integration.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.integration.export.ExportService.ExportSpec;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Streamed export and the read-only reporting schema, against real PostgreSQL. More than one batch of rows is created
 * so the keyset paging is exercised across batch boundaries.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportIT extends AbstractIntegrationTest {

    @Autowired SystemSettingService settingService;
    @Autowired ExportService exporter;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private String tag;
    private static final int ROWS = ExportService.BATCH * 2 + 300;   // two full batches and a partial one

    @BeforeAll
    void createRows() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            batch.add(new Object[] {"EXP-" + tag + "-" + String.format("%05d", i),
                    i == 7 ? "=HYPERLINK(\"http://evil.test\",\"click\")" : "Customer " + i});
        }
        jdbc.batchUpdate("INSERT INTO ims.customers (customer_code, name, status) VALUES (?, ?, 'ACTIVE')", batch);
    }

    @AfterAll
    void removeRows() {
        jdbc.update("DELETE FROM ims.sales_orders WHERE order_number LIKE ?", "EXP-SO-" + tag + "-%");
        jdbc.update("DELETE FROM ims.customers WHERE customer_code LIKE ?", "EXP-" + tag + "-%");
    }

    @BeforeEach
    void actAsExporter() {
        actAs("DATA_EXPORT");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UsernamePasswordAuthenticationToken token(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9104L, "export-it", true, 1, Set.of(), Set.of(authorities)), null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private void actAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(token(authorities));
    }

    private byte[] export(String entity, String format, LocalDate from, LocalDate to) throws Exception {
        ExportSpec spec = exporter.begin(entity, format, from, to);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.stream(spec, format, from, to, out);
        return out.toByteArray();
    }

    @Test
    void csvExportStreamsEveryRowOnceAcrossBatchesAndDefusesFormulas() throws Exception {
        String csv = new String(export("customers", "csv", null, null), StandardCharsets.UTF_8);
        List<CSVRecord> records = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(csv)).getRecords();

        List<CSVRecord> mine = records.stream().filter(r -> r.get("customer_code").startsWith("EXP-" + tag)).toList();
        assertThat(mine).hasSize(ROWS);
        assertThat(new HashSet<>(mine.stream().map(r -> r.get("customer_code")).toList())).as("no row twice").hasSize(ROWS);
        List<Long> ids = records.stream().map(r -> Long.parseLong(r.get("id"))).toList();
        assertThat(ids).as("ordered by id").isSorted();
        assertThat(mine.get(7).get("name")).as("a formula is defused with an apostrophe").startsWith("'=HYPERLINK");
        assertThat(records.get(0).toMap().keySet()).doesNotContain("contact_person", "email", "phone", "address");
    }

    @Test
    void jsonExportIsOneValidArrayWithTheSameRows() throws Exception {
        JsonNode array = mapper.readTree(export("customers", "json", null, null));
        assertThat(array.isArray()).isTrue();
        long mine = 0;
        for (JsonNode row : array) {
            if (row.path("customer_code").asText().startsWith("EXP-" + tag)) {
                mine++;
            }
        }
        assertThat(mine).isEqualTo(ROWS);
        assertThat(array.get(0).has("customer_code")).isTrue();
    }

    @Test
    void aDateFilterRestrictsEntitiesThatHaveADate() throws Exception {
        Long customerId = jdbc.queryForObject("SELECT id FROM ims.customers WHERE customer_code = ?", Long.class,
                "EXP-" + tag + "-00000");
        jdbc.update("INSERT INTO ims.sales_orders (order_number, customer_id, order_date, status) VALUES (?, ?, ?, 'DRAFT')",
                "EXP-SO-" + tag + "-A", customerId, LocalDate.of(2020, 1, 15));
        jdbc.update("INSERT INTO ims.sales_orders (order_number, customer_id, order_date, status) VALUES (?, ?, ?, 'DRAFT')",
                "EXP-SO-" + tag + "-B", customerId, LocalDate.of(2020, 3, 15));

        JsonNode inRange = mapper.readTree(export("sales_orders", "json", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 28)));
        List<String> numbers = new ArrayList<>();
        inRange.forEach(r -> numbers.add(r.path("order_number").asText()));
        assertThat(numbers).contains("EXP-SO-" + tag + "-A").doesNotContain("EXP-SO-" + tag + "-B");

        JsonNode openEnded = mapper.readTree(export("sales_orders", "json", LocalDate.of(2020, 2, 1), null));
        List<String> later = new ArrayList<>();
        openEnded.forEach(r -> later.add(r.path("order_number").asText()));
        assertThat(later).contains("EXP-SO-" + tag + "-B").doesNotContain("EXP-SO-" + tag + "-A");
    }

    @Test
    void everyExportableEntityStreamsWithoutError() throws Exception {
        actAs("DATA_EXPORT", "ANALYTICS_FINANCE");
        for (String entity : exporter.entities()) {
            assertThat(export(entity, "csv", null, null)).as(entity).isNotNull();
            assertThat(mapper.readTree(export(entity, "json", null, null)).isArray()).as(entity).isTrue();
        }
        assertThat(exporter.entities()).contains("customers", "inventory_transactions", "journal_lines");
    }

    @Test
    void financeEntitiesNeedTheFinancePermissionToo() {
        actAs("DATA_EXPORT");
        assertThat(exporter.entities()).doesNotContain("journal_lines", "ar_invoices", "ap_invoices").contains("customers");
        for (String entity : List.of("journal_lines", "ar_invoices", "ap_invoices")) {
            assertThatThrownBy(() -> exporter.begin(entity, "csv", null, null)).as(entity)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void unknownEntitiesBadFormatsAndTheExportRowCapAreHandled() throws Exception {
        assertThatThrownBy(() -> exporter.begin("users", "csv", null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> exporter.begin("pg_shadow", "csv", null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> exporter.begin("customers; DROP TABLE x", "csv", null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> exporter.begin("customers", "xml", null, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> exporter.begin(null, "csv", null, null)).isInstanceOf(NotFoundException.class);

        settingService.update("integration.export.max_rows", "10");
        try {
            JsonNode capped = mapper.readTree(export("customers", "json", null, null));
            assertThat(capped).as("the configured row cap stops the stream").hasSize(10);
        } finally {
            settingService.update("integration.export.max_rows", "1000000");
        }
    }

    @Test
    void theExportIsAuditedBeforeAnyDataLeaves() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'DATA_EXPORT' "
                + "AND entity = 'EXPORT_CUSTOMERS'", Integer.class);
        exporter.begin("customers", "csv", LocalDate.of(2026, 1, 1), null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'DATA_EXPORT' "
                + "AND entity = 'EXPORT_CUSTOMERS'", Integer.class)).isEqualTo(before + 1);
    }

    @Test
    void theEndpointStreamsForPermittedCallersAndRefusesTheRest() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/integration/export/customers").param("format", "csv")
                        .with(authentication(token("DATA_EXPORT"))))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult done = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();
        assertThat(done.getResponse().getContentType()).startsWith("text/csv");
        assertThat(done.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("customers-");
        assertThat(done.getResponse().getContentAsString()).startsWith("id,customer_code,");

        mockMvc.perform(get("/api/integration/export/customers").with(authentication(token("ANALYTICS_VIEW"))))
                .andExpect(status().isForbidden());
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/integration/export/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integration/export/nope").with(authentication(token("DATA_EXPORT"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/integration/export/journal_lines").with(authentication(token("DATA_EXPORT"))))
                .andExpect(status().isForbidden());
    }

    // ── the read-only reporting schema ───────────────────────────────────────────────────────────────────────────

    @Test
    void theReportingRoleReadsOnlyTheCuratedViews() {
        Boolean roleExists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ims_reporting')",
                Boolean.class);
        assertThat(roleExists).as("created by migration V34").isTrue();

        // each statement in its own transaction; SET LOCAL ROLE lasts only until that transaction ends
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        Integer readable = tx.execute(s -> {
            jdbc.execute("SET LOCAL ROLE ims_reporting");
            return jdbc.queryForObject("SELECT count(*) FROM reporting.customers", Integer.class);
        });
        assertThat(readable).isGreaterThanOrEqualTo(ROWS);

        for (String table : List.of("ims.users", "ims.customers", "ims.audit_logs", "ims.webhook_subscriptions",
                "ims.journal_lines", "ims.employees", "ims.payroll_slips")) {
            assertThatThrownBy(() -> tx.execute(s -> {
                jdbc.execute("SET LOCAL ROLE ims_reporting");
                return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
            })).as(table).satisfies(e -> assertThat(
                    org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                    .contains("permission denied"));
        }
        assertThatThrownBy(() -> tx.execute(s -> {
            jdbc.execute("SET LOCAL ROLE ims_reporting");
            jdbc.update("DELETE FROM reporting.customers");
            return null;
        })).as("the role cannot write").isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void noReportingViewExposesCredentialsPayOrContactDetails() {
        List<String> columns = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                 WHERE table_schema = 'reporting'
                   AND column_name ~* '(password|hash|salary|secret|token|bank|iban|ifsc|pf_|esi_|pan|email|phone|address|contact|gst)'
                """, String.class);
        assertThat(columns).as("sensitive columns in the reporting schema").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.views WHERE table_schema = 'reporting'",
                Integer.class)).isGreaterThanOrEqualTo(20);
    }
}
