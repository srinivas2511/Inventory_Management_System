package com.springmfg.ims.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
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

/** Task 1.8: materials over HTTP. */
@RecordApplicationEvents
class MaterialIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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

    private Map<String, Object> body(String code) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", "SS304 wire 2.5 mm");
        map.put("materialType", "STAINLESS");
        map.put("grade", "SS304");
        map.put("diameterMm", 2.5);
        map.put("uom", "KG");
        map.put("minStock", 100);
        map.put("reorderLevel", 200.5);
        map.put("maxStock", 1000);
        map.put("standardCost", 125.1234);
        map.put("shelfLifeDays", 365);
        map.put("description", "Cold drawn");
        return map;
    }

    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        return json.readTree(send(post("/api/materials"), token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long supplier(String token, String code) throws Exception {
        return json.readTree(send(post("/api/suppliers"), token, Map.of("code", code, "name", "Supplier " + code)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private AuditCommand lastEvent(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).reduce((a, b) -> b).orElseThrow();
    }

    private Map<String, Object> asUpdate(JsonNode created, Map<String, Object> changes) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", created.get("name").asText());
        update.put("materialType", created.get("materialType").asText());
        update.put("uom", created.get("uom").asText());
        update.put("minStock", created.get("minStock").decimalValue());
        update.put("reorderLevel", created.get("reorderLevel").decimalValue());
        update.put("version", created.get("version").asLong());
        update.putAll(changes);
        return update;
    }

    // ------------------------------------------------------------------------------------------- create

    @Test
    void createStoresAllFieldsWithTheDocumentedPrecision() throws Exception {
        String token = adminToken();
        String code = code("RM-T");
        long supplierId = supplier(token, code("SUP"));
        Map<String, Object> body = body(code);
        body.put("preferredSupplierId", supplierId);

        send(post("/api/materials"), token, body).andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/materials/")))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.materialType").value("STAINLESS"))
                .andExpect(jsonPath("$.diameterMm").value(2.5)).andExpect(jsonPath("$.reorderLevel").value(200.5))
                .andExpect(jsonPath("$.standardCost").value(125.1234)).andExpect(jsonPath("$.shelfLifeDays").value(365))
                .andExpect(jsonPath("$.preferredSupplier.id").value(supplierId)).andExpect(jsonPath("$.preferredSupplier.code").isNotEmpty())
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0));
        assertThat(jdbc.queryForObject("SELECT standard_cost::text FROM materials WHERE material_code = ?", String.class, code)).isEqualTo("125.1234");
        assertThat(jdbc.queryForObject("SELECT diameter_mm::text FROM materials WHERE material_code = ?", String.class, code)).isEqualTo("2.500");

        AuditCommand event = lastEvent("MATERIAL_CREATED");
        assertThat(event.newValue()).containsEntry("code", code).containsEntry("materialType", "STAINLESS").containsEntry("uom", "KG");
    }

    @Test
    void onlyCodeNameTypeAndUnitAreRequired() throws Exception {
        String token = adminToken();
        send(post("/api/materials"), token, Map.of("code", code("RM-M"), "name", "Packaging carton", "materialType", "CONSUMABLE", "uom", "PCS"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.minStock").value(0)).andExpect(jsonPath("$.reorderLevel").value(0))
                .andExpect(jsonPath("$.standardCost").value(0)).andExpect(jsonPath("$.maxStock").doesNotExist())
                .andExpect(jsonPath("$.preferredSupplier").doesNotExist());
    }

    @Test
    void stockLevelsMustBeOrdered() throws Exception {
        String token = adminToken();
        Map<String, Object> belowMin = body(code("RM-S"));
        belowMin.put("minStock", 300);
        belowMin.put("reorderLevel", 200);
        send(post("/api/materials"), token, belowMin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reorderLevel')]").exists());

        Map<String, Object> aboveMax = body(code("RM-S"));
        aboveMax.put("maxStock", 150);
        send(post("/api/materials"), token, aboveMax).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'maxStock')].message").value(hasItem(containsString("reorder level"))));

        Map<String, Object> equalLevels = body(code("RM-S"));
        equalLevels.put("minStock", 200);
        equalLevels.put("reorderLevel", 200);
        equalLevels.put("maxStock", 200);
        send(post("/api/materials"), token, equalLevels).andExpect(status().isCreated()); // min <= reorder <= max allows equality
    }

    @Test
    void referencesAreValidated() throws Exception {
        String token = adminToken();
        Map<String, Object> unknownUnit = body(code("RM-R"));
        unknownUnit.put("uom", "FURLONG");
        send(post("/api/materials"), token, unknownUnit).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("uom")).andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("FURLONG")));

        Map<String, Object> unknownSupplier = body(code("RM-R"));
        unknownSupplier.put("preferredSupplierId", 999999999L);
        send(post("/api/materials"), token, unknownSupplier).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("preferredSupplierId"));

        long inactive = supplier(token, code("SUP"));
        send(delete("/api/suppliers/" + inactive), token, null).andExpect(status().isNoContent());
        Map<String, Object> inactiveSupplier = body(code("RM-R"));
        inactiveSupplier.put("preferredSupplierId", inactive);
        send(post("/api/materials"), token, inactiveSupplier).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("inactive")));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        String token = adminToken();
        Map<String, Object> bad = body("bad code");
        bad.put("materialType", "WOOD");
        send(post("/api/materials"), token, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        Map<String, Object> tooPrecise = body(code("RM-V"));
        tooPrecise.put("minStock", 1.2345);
        tooPrecise.put("standardCost", 1.23456);
        send(post("/api/materials"), token, tooPrecise).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'minStock')]").exists()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'standardCost')]").exists());

        Map<String, Object> negative = body(code("RM-V"));
        negative.put("standardCost", -1);
        negative.put("shelfLifeDays", 0);
        send(post("/api/materials"), token, negative).andExpect(status().isBadRequest());

        Map<String, Object> noType = body(code("RM-V"));
        noType.remove("materialType");
        send(post("/api/materials"), token, noType).andExpect(status().isBadRequest());

        String duplicate = code("RM-V");
        create(token, body(duplicate));
        send(post("/api/materials"), token, body(duplicate)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
    }

    // ------------------------------------------------------------------------------------------- list

    @Test
    void listSearchesFiltersSortsAndPages() throws Exception {
        String token = adminToken();
        String tag = "LM" + SEQ.incrementAndGet() + "X";
        long supplierA = supplier(token, code("SUP"));
        Map<String, Object> a = body(tag + "-A");
        a.put("name", "Alpha " + tag);
        a.put("preferredSupplierId", supplierA);
        a.put("diameterMm", 3.0);
        Map<String, Object> b = body(tag + "-B");
        b.put("name", "Beta " + tag);
        b.put("materialType", "MUSIC_WIRE");
        b.put("grade", "MW-" + tag);
        b.put("uom", "G");
        b.put("diameterMm", 1.0);
        Map<String, Object> c = body(tag + "-C");
        c.put("name", "Gamma " + tag);
        create(token, a);
        create(token, b);
        long inactive = create(token, c).get("id").asLong();
        send(delete("/api/materials/" + inactive), token, null).andExpect(status().isNoContent());

        send(get("/api/materials?q=" + tag), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].code").value(tag + "-A")).andExpect(jsonPath("$.content[0].preferredSupplier.id").value(supplierA))
                .andExpect(jsonPath("$.content[1].preferredSupplier").doesNotExist());
        send(get("/api/materials?q=mw-" + tag.toLowerCase()), token, null).andExpect(jsonPath("$.totalElements").value(1)); // matches grade, any case
        send(get("/api/materials?q=" + tag + "&active=false"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/materials?q=" + tag + "&materialType=MUSIC_WIRE"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/materials?q=" + tag + "&supplierId=" + supplierA), token, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(tag + "-A"));
        send(get("/api/materials?q=" + tag + "&uom=G"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/materials?q=" + tag + "&sort=diameterMm,asc"), token, null).andExpect(jsonPath("$.content[0].code").value(tag + "-B"));
        send(get("/api/materials?q=" + tag + "&sort=code,desc&size=2&page=1"), token, null).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value(tag + "-A")).andExpect(jsonPath("$.totalPages").value(2));
        send(get("/api/materials?sort=description,asc"), token, null).andExpect(status().isBadRequest());
        send(get("/api/materials?materialType=WOOD"), token, null).andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsDetailOr404AndUnitsAreListed() throws Exception {
        String token = adminToken();
        JsonNode created = create(token, body(code("RM-G")));
        send(get("/api/materials/" + created.get("id").asLong()), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Cold drawn")).andExpect(jsonPath("$.updatedAt").isNotEmpty());
        send(get("/api/materials/999999999"), token, null).andExpect(status().isNotFound());

        send(get("/api/uoms"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.code == 'KG')].kind").value(hasItem("WEIGHT")));
        mockMvc.perform(get("/api/uoms")).andExpect(status().isUnauthorized());
        String operator = Api.login(mockMvc, json, testUsers.create("OPERATOR").getUsername(), TestUsers.PASSWORD).accessToken();
        send(get("/api/uoms"), operator, null).andExpect(status().isOk()); // reference data for any signed-in user
    }

    // ------------------------------------------------------------------------------------------- update

    @Test
    void updateReplacesEditableFieldsChecksTheVersionAndKeepsTheCode() throws Exception {
        String token = adminToken();
        JsonNode created = create(token, body(code("RM-U")));
        long id = created.get("id").asLong();
        Map<String, Object> update = asUpdate(created, Map.of("name", "Renamed", "grade", "SS316", "standardCost", 130));

        send(put("/api/materials/" + id), token, update).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.grade").value("SS316")).andExpect(jsonPath("$.code").value(created.get("code").asText()))
                .andExpect(jsonPath("$.maxStock").doesNotExist()) // full replacement: omitted optional fields are cleared
                .andExpect(jsonPath("$.version").value(1));
        AuditCommand event = lastEvent("MATERIAL_UPDATED");
        assertThat(event.oldValue()).containsEntry("name", "SS304 wire 2.5 mm").containsEntry("grade", "SS304");
        assertThat(event.newValue()).containsEntry("name", "Renamed").containsEntry("grade", "SS316");

        send(put("/api/materials/" + id), token, update).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(put("/api/materials/999999999"), token, update).andExpect(status().isNotFound());
        Map<String, Object> badLevels = asUpdate(json.readTree(fetch(token, id)), Map.of("minStock", 500, "reorderLevel", 100));
        send(put("/api/materials/" + id), token, badLevels).andExpect(status().isBadRequest());
    }

    private String fetch(String token, long id) throws Exception {
        return send(get("/api/materials/" + id), token, null).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theUnitOfMeasureCanChangeUntilSomethingUsesTheMaterial() throws Exception {
        String token = adminToken();
        JsonNode free = create(token, body(code("RM-F")));
        send(put("/api/materials/" + free.get("id").asLong()), token, asUpdate(free, Map.of("uom", "G"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.uom").value("G"));

        JsonNode locked = create(token, body("LOCK." + code("U")));
        send(put("/api/materials/" + locked.get("id").asLong()), token, asUpdate(locked, Map.of("uom", "G"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNIT_CHANGE_BLOCKED")).andExpect(jsonPath("$.detail").value(containsString("stock exists in KG")));
        // other edits of a locked material are fine, and re-sending the same unit is not a change
        send(put("/api/materials/" + locked.get("id").asLong()), token, asUpdate(locked, Map.of("name", "Still editable"))).andExpect(status().isOk());
    }

    @Test
    void aSupplierThatLaterBecameInactiveMayStayButCannotBeChosenAgain() throws Exception {
        String token = adminToken();
        long supplierId = supplier(token, code("SUP"));
        Map<String, Object> body = body(code("RM-P"));
        body.put("preferredSupplierId", supplierId);
        JsonNode created = create(token, body);
        send(delete("/api/suppliers/" + supplierId), token, null).andExpect(status().isNoContent()); // blocks new documents only

        Map<String, Object> keep = asUpdate(created, Map.of("name", "Edited", "preferredSupplierId", supplierId));
        send(put("/api/materials/" + created.get("id").asLong()), token, keep).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSupplier.id").value(supplierId));

        long other = supplier(token, code("SUP"));
        send(delete("/api/suppliers/" + other), token, null).andExpect(status().isNoContent());
        JsonNode current = json.readTree(fetch(token, created.get("id").asLong()));
        send(put("/api/materials/" + created.get("id").asLong()), token, asUpdate(current, Map.of("preferredSupplierId", other)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("preferredSupplierId"));
        send(put("/api/materials/" + created.get("id").asLong()), token, asUpdate(current, Map.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSupplier").doesNotExist()); // omitted = cleared
    }

    // ------------------------------------------------------------------------------------------- deactivate

    @Test
    void deactivatingAFreeMaterialJustWorksAndCanBeUndone() throws Exception {
        String token = adminToken();
        long id = create(token, body(code("RM-D"))).get("id").asLong();
        mockMvc.perform(Api.bearer(delete("/api/materials/" + id).param("reason", "discontinued"), token)).andExpect(status().isNoContent());
        send(get("/api/materials/" + id), token, null).andExpect(jsonPath("$.active").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM materials WHERE id = ?", Integer.class, id)).isEqualTo(1); // never deleted
        AuditCommand event = lastEvent("MATERIAL_DEACTIVATED");
        assertThat(event.reason()).isEqualTo("discontinued");
        assertThat(event.newValue()).containsEntry("active", false).doesNotContainKey("deactivatedDespite");

        send(delete("/api/materials/" + id), token, null).andExpect(status().isNoContent()); // idempotent
        send(post("/api/materials/" + id + "/activate"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(lastEvent("MATERIAL_ACTIVATED").newValue()).containsEntry("active", true);
    }

    @Test
    void aMaterialUsedByActiveProductsNeedsForceToDeactivate() throws Exception {
        String token = adminToken();
        long id = create(token, body(code("RM-X"))).get("id").asLong();
        jdbc.update("INSERT INTO products (product_code, name, spring_type, primary_material_id) VALUES (?, 'P', 'COMPRESSION', ?)", code("SPR"), id);

        send(delete("/api/materials/" + id), token, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEACTIVATION_BLOCKED")).andExpect(jsonPath("$.detail").value(containsString("1 active product uses it")))
                .andExpect(jsonPath("$.detail").value(containsString("force=true")));
        send(get("/api/materials/" + id), token, null).andExpect(jsonPath("$.active").value(true)); // nothing changed

        send(delete("/api/materials/" + id + "?force=true"), token, null).andExpect(status().isNoContent());
        send(get("/api/materials/" + id), token, null).andExpect(jsonPath("$.active").value(false));
        assertThat(lastEvent("MATERIAL_DEACTIVATED").newValue()).containsKey("deactivatedDespite");
    }

    @Test
    void inactiveProductsDoNotBlockDeactivation() throws Exception {
        String token = adminToken();
        long id = create(token, body(code("RM-Y"))).get("id").asLong();
        jdbc.update("INSERT INTO products (product_code, name, spring_type, primary_material_id, active) VALUES (?, 'P', 'COMPRESSION', ?, FALSE)", code("SPR"), id);
        send(delete("/api/materials/" + id), token, null).andExpect(status().isNoContent());
    }

    @Test
    void otherModulesCanHoldAMaterialThroughMaterialUsageCheck() throws Exception {
        // TestMaterialUsageCheck stands in for the stock/purchasing checks of Phases 2-3 (codes starting LOCK.)
        String token = adminToken();
        long id = create(token, body("LOCK." + code("S"))).get("id").asLong();
        send(delete("/api/materials/" + id), token, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("12.000 KG in stock; 2 open purchase orders")));
        send(delete("/api/materials/" + id + "?force=true&reason=agreed"), token, null).andExpect(status().isNoContent());
        AuditCommand event = lastEvent("MATERIAL_DEACTIVATED");
        assertThat(event.newValue().get("deactivatedDespite")).isEqualTo(List.of("12.000 KG in stock", "2 open purchase orders"));
    }

    @Test
    void everyChangeIsAudited() throws Exception {
        String token = adminToken();
        JsonNode created = create(token, body(code("RM-AU")));
        long id = created.get("id").asLong();
        send(put("/api/materials/" + id), token, asUpdate(created, Map.of("name", "Changed"))).andExpect(status().isOk());
        send(delete("/api/materials/" + id), token, null).andExpect(status().isNoContent());
        send(post("/api/materials/" + id + "/activate"), token, null).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity = 'Material' AND entity_id = ? ORDER BY id", String.class, String.valueOf(id)))
                .containsExactly("MATERIAL_CREATED", "MATERIAL_UPDATED", "MATERIAL_DEACTIVATED", "MATERIAL_ACTIVATED");
    }
}
