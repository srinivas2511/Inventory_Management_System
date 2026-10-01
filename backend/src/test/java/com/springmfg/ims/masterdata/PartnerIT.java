package com.springmfg.ims.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/** Task 1.8: suppliers and customers behave identically; every test runs against both. */
@RecordApplicationEvents
class PartnerIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String GST = "27AAPFU0939F1ZV";

    /** One of the two resources under test. */
    private record Kind(String path, String entity, String table, String codeColumn, String actionPrefix) {
    }

    private static final Kind SUPPLIERS = new Kind("/api/suppliers", "Supplier", "suppliers", "supplier_code", "SUPPLIER");
    private static final Kind CUSTOMERS = new Kind("/api/customers", "Customer", "customers", "customer_code", "CUSTOMER");

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ApplicationEvents events;

    private String adminToken() throws Exception {
        return Api.login(mockMvc, json, testUsers.create("ADMIN").getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        var r = Api.bearer(request, token);
        return mockMvc.perform(body == null ? r : Api.jsonBody(r, json, body));
    }

    private static String code(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + Long.toString(System.nanoTime() % 100000, 36).toUpperCase();
    }

    private Map<String, Object> body(String code, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("contactPerson", "A. Person");
        map.put("phone", "+91 44 2345 6789");
        map.put("email", "Buyer@Example.COM");
        map.put("address", "12 Industrial Estate, Chennai");
        map.put("gstNumber", GST);
        map.put("paymentTerms", "30 days");
        map.put("leadTimeDays", 14);
        return map;
    }

    private JsonNode create(Kind kind, String token, String code, String name) throws Exception {
        return json.readTree(send(post(kind.path()), token, body(code, name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private AuditCommand lastEvent(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).reduce((a, b) -> b).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------- create

    @Test
    void suppliersCreateReadAndNormalise() throws Exception {
        createReadAndNormalise(SUPPLIERS);
    }

    @Test
    void customersCreateReadAndNormalise() throws Exception {
        createReadAndNormalise(CUSTOMERS);
    }

    private void createReadAndNormalise(Kind kind) throws Exception {
        String token = adminToken();
        String code = code("N");
        send(post(kind.path()), token, body(code, "  Sundaram Wires  ")).andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(kind.path() + "/")))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.name").value("Sundaram Wires"))
                .andExpect(jsonPath("$.email").value("buyer@example.com")) // stored lower-case
                .andExpect(jsonPath("$.gstNumber").value(GST)).andExpect(jsonPath("$.leadTimeDays").value(14))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM " + kind.table() + " WHERE " + kind.codeColumn() + " = ?", String.class, code))
                .isEqualTo("ACTIVE");

        AuditCommand event = lastEvent(kind.actionPrefix() + "_CREATED");
        assertThat(event.entity()).isEqualTo(kind.entity());
        assertThat(event.oldValue()).isNull();
        assertThat(event.newValue()).containsEntry("code", code).containsEntry("gstNumber", GST);
    }

    @Test
    void optionalFieldsMayBeOmitted() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            send(post(kind.path()), token, Map.of("code", code("O"), "name", "Minimal")).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.gstNumber").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        }
    }

    @Test
    void duplicateCodesAreConflicts() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            String code = code("D");
            create(kind, token, code, "First");
            send(post(kind.path()), token, body(code, "Second")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
        }
    }

    @Test
    void invalidInputIsReportedPerField() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            Map<String, Object> bad = body("bad code", " ");
            bad.put("gstNumber", "27AAPFU0939F1XV");
            bad.put("email", "nope");
            bad.put("leadTimeDays", -3);
            send(post(kind.path()), token, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'code')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'gstNumber')].message").value(org.hamcrest.Matchers.hasItem("Not a valid 15-character GST number")))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'leadTimeDays')]").exists());

            Map<String, Object> overPost = body(code("X"), "Extra");
            overPost.put("active", false); // not a field of the request: rejected, not ignored
            send(post(kind.path()), token, overPost).andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------------------------------- list

    @Test
    void listSearchesFiltersSortsAndPages() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            String tag = "LST" + SEQ.incrementAndGet() + "X";
            create(kind, token, tag + "-A", "Alpha " + tag);
            create(kind, token, tag + "-B", "Beta " + tag);
            long off = create(kind, token, tag + "-C", "Gamma " + tag).get("id").asLong();
            send(delete(kind.path() + "/" + off), token, null).andExpect(status().isNoContent());

            send(get(kind.path() + "?q=" + tag.toLowerCase()), token, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content[0].code").value(tag + "-A"))
                    .andExpect(jsonPath("$.content[0].email").value("buyer@example.com"));
            send(get(kind.path() + "?q=" + tag + "&active=false"), token, null).andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(Api.bearer(get(kind.path()).param("q", "Beta " + tag), token)).andExpect(jsonPath("$.totalElements").value(1)); // matches name
            send(get(kind.path() + "?q=" + tag + "&sort=code,desc&size=2"), token, null).andExpect(jsonPath("$.content[0].code").value(tag + "-C"))
                    .andExpect(jsonPath("$.totalPages").value(2));
            send(get(kind.path() + "?q=" + tag + "&sort=name,asc"), token, null).andExpect(jsonPath("$.content[0].name").value("Alpha " + tag));
            send(get(kind.path() + "?sort=address,asc"), token, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
            send(get(kind.path() + "?size=999"), token, null).andExpect(jsonPath("$.size").value(100));
        }
    }

    @Test
    void getReturnsOneOr404() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            long id = create(kind, token, code("G"), "Gettable").get("id").asLong();
            send(get(kind.path() + "/" + id), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.address").value("12 Industrial Estate, Chennai"));
            send(get(kind.path() + "/999999999"), token, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------------------------------- update

    @Test
    void updateReplacesTheEditableFieldsChecksTheVersionAndKeepsTheCode() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            JsonNode created = create(kind, token, code("U"), "Before");
            long id = created.get("id").asLong();
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("name", "After");
            update.put("phone", "99999");
            update.put("gstNumber", "29GGGGG1314R9Z6");
            update.put("version", created.get("version").asLong());
            send(put(kind.path() + "/" + id), token, update).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("After"))
                    .andExpect(jsonPath("$.code").value(created.get("code").asText())).andExpect(jsonPath("$.email").doesNotExist()) // full replacement
                    .andExpect(jsonPath("$.gstNumber").value("29GGGGG1314R9Z6")).andExpect(jsonPath("$.version").value(1));

            AuditCommand event = lastEvent(kind.actionPrefix() + "_UPDATED");
            assertThat(event.oldValue()).containsEntry("name", "Before").containsEntry("gstNumber", GST);
            assertThat(event.newValue()).containsEntry("name", "After").containsEntry("gstNumber", "29GGGGG1314R9Z6");

            send(put(kind.path() + "/" + id), token, update).andExpect(status().isConflict()) // stale version
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
            Map<String, Object> noVersion = new LinkedHashMap<>(update);
            noVersion.remove("version");
            send(put(kind.path() + "/" + id), token, noVersion).andExpect(status().isBadRequest());
            send(put(kind.path() + "/999999999"), token, update).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------------------------------- deactivate

    @Test
    void deactivationKeepsTheRecordIsIdempotentAndReversible() throws Exception {
        String token = adminToken();
        for (Kind kind : new Kind[] { SUPPLIERS, CUSTOMERS }) {
            String code = code("A");
            long id = create(kind, token, code, "Soon inactive").get("id").asLong();

            mockMvc.perform(Api.bearer(delete(kind.path() + "/" + id).param("reason", "closed down"), token)).andExpect(status().isNoContent());
            send(get(kind.path() + "/" + id), token, null).andExpect(jsonPath("$.active").value(false)); // still readable
            assertThat(jdbc.queryForObject("SELECT status FROM " + kind.table() + " WHERE id = ?", String.class, id)).isEqualTo("INACTIVE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + kind.table() + " WHERE id = ?", Integer.class, id)).isEqualTo(1);

            AuditCommand event = lastEvent(kind.actionPrefix() + "_DEACTIVATED");
            assertThat(event.reason()).isEqualTo("closed down");
            assertThat(event.oldValue()).containsEntry("active", true);
            assertThat(event.newValue()).containsEntry("active", false);

            long before = events.stream(AuditCommand.class).filter(e -> e.action().equals(kind.actionPrefix() + "_DEACTIVATED")).count();
            send(delete(kind.path() + "/" + id), token, null).andExpect(status().isNoContent());
            assertThat(events.stream(AuditCommand.class).filter(e -> e.action().equals(kind.actionPrefix() + "_DEACTIVATED")).count()).isEqualTo(before);

            send(post(kind.path() + "/" + id + "/activate"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
            assertThat(jdbc.queryForObject("SELECT status FROM " + kind.table() + " WHERE id = ?", String.class, id)).isEqualTo("ACTIVE");
            send(delete(kind.path() + "/999999999"), token, null).andExpect(status().isNotFound());
        }
    }

    @Test
    void auditRowsAreWrittenForEveryChange() throws Exception {
        String token = adminToken();
        String code = code("AU");
        JsonNode created = create(SUPPLIERS, token, code, "Audited");
        long id = created.get("id").asLong();
        Map<String, Object> update = Map.of("name", "Audited 2", "version", created.get("version").asLong());
        send(put("/api/suppliers/" + id), token, update).andExpect(status().isOk());
        send(delete("/api/suppliers/" + id), token, null).andExpect(status().isNoContent());
        send(post("/api/suppliers/" + id + "/activate"), token, null).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity = 'Supplier' AND entity_id = ? ORDER BY id", String.class,
                String.valueOf(id))).containsExactly("SUPPLIER_CREATED", "SUPPLIER_UPDATED", "SUPPLIER_DEACTIVATED", "SUPPLIER_ACTIVATED");
    }
}
