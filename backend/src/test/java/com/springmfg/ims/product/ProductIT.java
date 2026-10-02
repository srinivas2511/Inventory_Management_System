package com.springmfg.ims.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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

/** Task 1.9: products and the attribute catalogue over HTTP, for all seven spring types. */
@RecordApplicationEvents
class ProductIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ApplicationEvents events;

    private String engineerToken() throws Exception {
        return Api.login(mockMvc, json, testUsers.create("ENGINEER").getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        var r = Api.bearer(request, token);
        return mockMvc.perform(body == null ? r : Api.jsonBody(r, json, body));
    }

    private static String code(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + Long.toString(System.nanoTime() % 100000, 36).toUpperCase();
    }

    private long material(String active) {
        return jdbc.queryForObject("INSERT INTO materials (material_code, name, material_type, uom, active) VALUES (?, 'Mat', 'ALLOY', 'KG', ?::boolean) RETURNING id",
                Long.class, code("RM-P"), active);
    }

    private long customer(boolean active) {
        return jdbc.queryForObject("INSERT INTO customers (customer_code, name, active, status) VALUES (?, 'Cust', ?, ?) RETURNING id", Long.class,
                code("CU"), active, active ? "ACTIVE" : "INACTIVE");
    }

    private AuditCommand lastEvent(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).reduce((a, b) -> b).orElseThrow();
    }

    /** A complete, valid product of each type: core fields at the top level, the rest in specifications. */
    private static Map<String, Object> valid(SpringType type, String productCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", productCode);
        body.put("name", type.label() + " spring");
        body.put("springType", type.name());
        Map<String, Object> specs = new LinkedHashMap<>();
        switch (type) {
            case COMPRESSION -> {
                body.putAll(Map.of("wireDiameter", 2.5, "outerDiameter", 20, "freeLength", 50, "numberOfCoils", 8.5, "activeCoils", 6.5,
                        "springRate", 12.4, "maxLoad", 180, "solidHeight", 21.5, "endType", "CLOSED_GROUND"));
                specs.put("coilDirection", "RIGHT");
            }
            case EXTENSION -> {
                body.putAll(Map.of("wireDiameter", 1.2, "outerDiameter", 12, "freeLength", 60, "numberOfCoils", 20, "springRate", 3.1));
                specs.putAll(Map.of("hookType", "MACHINE_HOOK", "hookLength", 10, "initialTension", 4.5));
            }
            case TORSION -> {
                body.putAll(Map.of("wireDiameter", 1.5, "outerDiameter", 15, "numberOfCoils", 6));
                specs.putAll(Map.of("legLength", 25, "legAngle", 90, "torque", 120, "coilDirection", "LEFT"));
            }
            case CONICAL -> {
                body.putAll(Map.of("wireDiameter", 3, "outerDiameter", 40, "freeLength", 55, "numberOfCoils", 5, "springRate", 20, "maxLoad", 400));
                specs.put("smallOuterDiameter", 18);
            }
            case DISC_BELLEVILLE -> {
                body.putAll(Map.of("outerDiameter", 50, "innerDiameter", 20.4, "freeLength", 3.9, "maxLoad", 5000));
                specs.putAll(Map.of("thickness", 2.5, "stackCount", 3, "stackArrangement", "SERIES"));
            }
            case WIRE_FORM -> {
                body.put("wireDiameter", 2);
                specs.putAll(Map.of("developedLength", 310, "numberOfBends", 4, "formDescription", "S-hook"));
            }
            case CUSTOM -> specs.putAll(Map.of("anyKey", "x", "turns", 3, "special", true));
        }
        body.put("specifications", specs);
        return body;
    }

    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        return json.readTree(send(post("/api/products"), token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** The body of a PUT that re-sends the product as it is, with the given changes applied. */
    private Map<String, Object> asUpdate(JsonNode product, Map<String, Object> changes) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String field : List.of("name", "springType", "wireDiameter", "outerDiameter", "innerDiameter", "freeLength", "numberOfCoils",
                "activeCoils", "springRate", "maxLoad", "minLoad", "workingLength", "solidHeight", "endType", "surfaceTreatment",
                "heatTreatment", "tolerance", "unitWeightKg", "uom", "drawingNumber", "drawingRevision", "reorderLevel", "standardCost")) {
            if (product.hasNonNull(field)) {
                body.put(field, product.get(field).isNumber() ? product.get(field).decimalValue() : product.get(field).asText());
            }
        }
        if (product.hasNonNull("primaryMaterial")) {
            body.put("primaryMaterialId", product.get("primaryMaterial").get("id").asLong());
        }
        if (product.hasNonNull("customer")) {
            body.put("customerId", product.get("customer").get("id").asLong());
        }
        body.put("specifications", json.convertValue(product.get("specifications"), Map.class));
        body.put("version", product.get("version").asLong());
        body.putAll(changes);
        return body;
    }

    // ------------------------------------------------------------------------------------------- the catalogue

    @Test
    void theCatalogueListsTheSevenSpringTypesWithTheirAttributeCounts() throws Exception {
        String token = engineerToken();
        send(get("/api/spring-types"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.type == 'COMPRESSION')].attributeCount").value(hasItem(10)))
                .andExpect(jsonPath("$[?(@.type == 'EXTENSION')].attributeCount").value(hasItem(8)))
                .andExpect(jsonPath("$[?(@.type == 'TORSION')].attributeCount").value(hasItem(7)))
                .andExpect(jsonPath("$[?(@.type == 'CONICAL')].attributeCount").value(hasItem(7)))
                .andExpect(jsonPath("$[?(@.type == 'DISC_BELLEVILLE')].attributeCount").value(hasItem(7)))
                .andExpect(jsonPath("$[?(@.type == 'WIRE_FORM')].attributeCount").value(hasItem(4)))
                .andExpect(jsonPath("$[?(@.type == 'CUSTOM')].attributeCount").value(hasItem(0)))
                .andExpect(jsonPath("$[?(@.type == 'CUSTOM')].freeForm").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.type == 'COMPRESSION')].freeForm").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.type == 'DISC_BELLEVILLE')].label").value(hasItem("Disc / Belleville")));
    }

    @Test
    void attributesAreServedInDisplayOrderWithTheirLimitsUnitsAndStorage() throws Exception {
        String token = engineerToken();
        send(get("/api/spring-types/COMPRESSION/attributes"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("wireDiameter")).andExpect(jsonPath("$[0].label").value("Wire diameter"))
                .andExpect(jsonPath("$[0].dataType").value("NUMBER")).andExpect(jsonPath("$[0].unit").value("mm"))
                .andExpect(jsonPath("$[0].required").value(true)).andExpect(jsonPath("$[0].minValue").value(0.001))
                .andExpect(jsonPath("$[0].storage").value("CORE"))
                .andExpect(jsonPath("$[?(@.code == 'endType')].enumValues[0]").value(hasItem("PLAIN")))
                .andExpect(jsonPath("$[?(@.code == 'endType')].storage").value(hasItem("CORE")))
                .andExpect(jsonPath("$[?(@.code == 'coilDirection')].storage").value(hasItem("SPECIFICATIONS")))
                .andExpect(jsonPath("$[?(@.code == 'coilDirection')].required").value(hasItem(false)));
        send(get("/api/spring-types/EXTENSION/attributes"), token, null)
                .andExpect(jsonPath("$[?(@.code == 'outerDiameter')].label").value(hasItem("Body outer diameter"))) // relabelled per type
                .andExpect(jsonPath("$[?(@.code == 'hookType')].required").value(hasItem(true)));
        send(get("/api/spring-types/TORSION/attributes"), token, null)
                .andExpect(jsonPath("$[?(@.code == 'legAngle')].maxValue").value(hasItem(360)))
                .andExpect(jsonPath("$[?(@.code == 'legAngle')].unit").value(hasItem("deg")));
        send(get("/api/spring-types/DISC_BELLEVILLE/attributes"), token, null)
                .andExpect(jsonPath("$[?(@.code == 'thickness')].storage").value(hasItem("SPECIFICATIONS")))
                .andExpect(jsonPath("$[?(@.code == 'freeLength')].label").value(hasItem("Free height")));
        send(get("/api/spring-types/CUSTOM/attributes"), token, null).andExpect(jsonPath("$.length()").value(0));
        send(get("/api/spring-types/NOT_A_TYPE/attributes"), token, null).andExpect(status().isBadRequest());
    }

    @Test
    void addingAnAttributeDefinitionRowChangesTheFormAndTheValidationWithoutCode() throws Exception {
        String token = engineerToken();
        JsonNode product = create(token, valid(SpringType.COMPRESSION, code("SPR-DYN")));
        send(post("/api/products/" + product.get("id").asLong() + "/activate"), token, null).andExpect(status().isOk());
        String attribute = "testFinish" + SEQ.incrementAndGet();
        try {
            // before: the key is unknown
            Map<String, Object> withNew = valid(SpringType.COMPRESSION, code("SPR-DYN"));
            ((Map<String, Object>) withNew.get("specifications")).put(attribute, "MATT");
            send(post("/api/products"), token, withNew).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("Unknown attribute")));

            jdbc.update("""
                    INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, required, enum_values, display_order)
                    VALUES ('COMPRESSION', ?, 'Finish', 'ENUM', TRUE, '["MATT","GLOSS"]'::jsonb, 999)""", attribute);

            // the form now offers it ...
            send(get("/api/spring-types/COMPRESSION/attributes"), token, null)
                    .andExpect(jsonPath("$[?(@.code == '" + attribute + "')].label").value(hasItem("Finish")))
                    .andExpect(jsonPath("$[?(@.code == '" + attribute + "')].storage").value(hasItem("SPECIFICATIONS")))
                    .andExpect(jsonPath("$[?(@.code == '" + attribute + "')].enumValues[1]").value(hasItem("GLOSS")));
            send(get("/api/spring-types"), token, null).andExpect(jsonPath("$[?(@.type == 'COMPRESSION')].attributeCount").value(hasItem(11)));
            // ... the validator accepts it and checks its values ...
            send(post("/api/products"), token, withNew).andExpect(status().isCreated());
            ((Map<String, Object>) withNew.get("specifications")).put(attribute, "SPARKLY");
            withNew.put("productCode", code("SPR-DYN"));
            send(post("/api/products"), token, withNew).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("specifications." + attribute))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("MATT, GLOSS")));
            // ... and, being required, it now blocks activating a product that lacks it
            JsonNode lacking = create(token, valid(SpringType.COMPRESSION, code("SPR-DYN")));
            send(post("/api/products/" + lacking.get("id").asLong() + "/activate"), token, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("specifications." + attribute));
        } finally {
            jdbc.update("DELETE FROM spring_attribute_definitions WHERE attribute_code = ?", attribute);
        }
        send(get("/api/spring-types/COMPRESSION/attributes"), token, null)
                .andExpect(jsonPath("$[?(@.code == '" + attribute + "')]").isEmpty()); // and removing the row removes it again
    }

    // ------------------------------------------------------------------------------------------- all seven types

    @Test
    void everySpringTypeCanBeCreatedActivatedAndReadBack() throws Exception {
        String token = engineerToken();
        for (SpringType type : SpringType.values()) {
            Map<String, Object> body = valid(type, code("SPR-" + type.name().substring(0, 3)));
            JsonNode created = create(token, body);
            long id = created.get("id").asLong();
            assertThat(created.get("status").asText()).as(type.name()).isEqualTo("DRAFT");
            assertThat(created.get("springType").asText()).isEqualTo(type.name());

            send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
            JsonNode fetched = json.readTree(send(get("/api/products/" + id), token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

            @SuppressWarnings("unchecked")
            Map<String, Object> sent = (Map<String, Object>) body.get("specifications");
            assertThat(fetched.get("specifications").size()).as(type + " specifications").isEqualTo(sent.size());
            sent.forEach((key, value) -> {
                JsonNode stored = fetched.get("specifications").get(key);
                if (value instanceof Number n) {
                    assertThat(stored.decimalValue()).as(type + "." + key).isEqualByComparingTo(new java.math.BigDecimal(n.toString()));
                } else if (value instanceof Boolean b) {
                    assertThat(stored.asBoolean()).isEqualTo(b);
                } else {
                    assertThat(stored.asText()).isEqualTo(value.toString());
                }
            });
            assertThat(jdbc.queryForObject("SELECT jsonb_typeof(attributes) FROM spring_specifications WHERE product_id = ?", String.class, id)).isEqualTo("object");
        }
    }

    @Test
    void everyRequiredAttributeOfEveryTypeBlocksActivationWhenMissing() throws Exception {
        String token = engineerToken();
        int checked = 0;
        for (SpringType type : SpringType.values()) {
            JsonNode attributes = json.readTree(send(get("/api/spring-types/" + type + "/attributes"), token, null).andReturn().getResponse().getContentAsString());
            for (JsonNode attribute : attributes) {
                if (!attribute.get("required").asBoolean()) {
                    continue;
                }
                String attr = attribute.get("code").asText();
                boolean core = attribute.get("storage").asText().equals("CORE");
                Map<String, Object> body = valid(type, code("SPR-REQ"));
                @SuppressWarnings("unchecked")
                Map<String, Object> specs = (Map<String, Object>) body.get("specifications");
                if (core) {
                    body.remove(attr);
                } else {
                    specs.remove(attr);
                }
                long id = create(token, body).get("id").asLong(); // a draft may be unfinished ...
                String field = core ? attr : "specifications." + attr;
                send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isBadRequest()) // ... but cannot be activated
                        .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')].message").value(hasItem(attribute.get("label").asText() + " is required.")));
                checked++;
            }
        }
        assertThat(checked).as("required attributes across the seven types").isEqualTo(25);
    }

    @Test
    void aDraftWithOnlyANameAndTypeCanBeSavedAndListsAllWhatActivationNeeds() throws Exception {
        String token = engineerToken();
        long id = create(token, Map.of("productCode", code("SPR-MIN"), "name", "Unfinished", "springType", "TORSION")).get("id").asLong();
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(5)) // wire, coil diameter, coils, leg length, leg angle
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("wireDiameter", "outerDiameter", "numberOfCoils",
                        "specifications.legLength", "specifications.legAngle")));
        send(get("/api/products/" + id), token, null).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ------------------------------------------------------------------------------------------- validation

    @Test
    void invalidSpecificationsAreRejectedPerField() throws Exception {
        String token = engineerToken();
        Map<String, Object> unknown = valid(SpringType.TORSION, code("SPR-V"));
        spec(unknown).put("colour", "red");
        send(post("/api/products"), token, unknown).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("specifications.colour"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("Allowed: coilDirection, legAngle, legLength, torque")));

        Map<String, Object> core = valid(SpringType.TORSION, code("SPR-V"));
        spec(core).put("wireDiameter", 2);
        send(post("/api/products"), token, core).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("top level")));

        Map<String, Object> range = valid(SpringType.TORSION, code("SPR-V"));
        spec(range).put("legAngle", 400);
        send(post("/api/products"), token, range).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("specifications.legAngle")).andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("at most 360")));

        Map<String, Object> text = valid(SpringType.TORSION, code("SPR-V"));
        spec(text).put("legLength", "twenty");
        send(post("/api/products"), token, text).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("must be a number")));

        Map<String, Object> enumBad = valid(SpringType.COMPRESSION, code("SPR-V"));
        enumBad.put("endType", "FANCY");
        send(post("/api/products"), token, enumBad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("endType"));

        Map<String, Object> belowMin = valid(SpringType.COMPRESSION, code("SPR-V"));
        belowMin.put("wireDiameter", 0);
        send(post("/api/products"), token, belowMin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("wireDiameter"));

        Map<String, Object> customBad = valid(SpringType.CUSTOM, code("SPR-V"));
        spec(customBad).put("bad key", 1);
        send(post("/api/products"), token, customBad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("specifications.bad key"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> spec(Map<String, Object> body) {
        return (Map<String, Object>) body.get("specifications");
    }

    @Test
    void geometryAndTheDocumentedFormatsAreChecked() throws Exception {
        String token = engineerToken();
        Map<String, Object> inner = valid(SpringType.DISC_BELLEVILLE, code("SPR-G"));
        inner.put("innerDiameter", 60);
        send(post("/api/products"), token, inner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'innerDiameter')]").exists());

        Map<String, Object> coils = valid(SpringType.COMPRESSION, code("SPR-G"));
        coils.put("activeCoils", 20);
        send(post("/api/products"), token, coils).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'activeCoils')]").exists());

        Map<String, Object> loads = valid(SpringType.COMPRESSION, code("SPR-G"));
        loads.put("minLoad", 500);
        send(post("/api/products"), token, loads).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'minLoad')]").exists());

        Map<String, Object> precision = valid(SpringType.COMPRESSION, code("SPR-G"));
        precision.put("wireDiameter", 2.5001);
        send(post("/api/products"), token, precision).andExpect(status().isBadRequest());

        Map<String, Object> badCode = valid(SpringType.COMPRESSION, "bad code");
        send(post("/api/products"), token, badCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("productCode"));
        Map<String, Object> noCode = valid(SpringType.COMPRESSION, "X");
        noCode.remove("productCode");
        send(post("/api/products"), token, noCode).andExpect(status().isBadRequest());

        Map<String, Object> noName = valid(SpringType.COMPRESSION, code("SPR-G"));
        noName.put("name", " ");
        send(post("/api/products"), token, noName).andExpect(status().isBadRequest());
        Map<String, Object> unknownField = valid(SpringType.COMPRESSION, code("SPR-G"));
        unknownField.put("status", "ACTIVE"); // status cannot be set through the body
        send(post("/api/products"), token, unknownField).andExpect(status().isBadRequest());
        Map<String, Object> badUnit = valid(SpringType.COMPRESSION, code("SPR-G"));
        badUnit.put("uom", "FURLONG");
        send(post("/api/products"), token, badUnit).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("uom"));

        String duplicate = code("SPR-G");
        create(token, valid(SpringType.COMPRESSION, duplicate));
        send(post("/api/products"), token, valid(SpringType.COMPRESSION, duplicate)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
    }

    @Test
    void referencesToMaterialsAndCustomersMustBeActiveWhenChosen() throws Exception {
        String token = engineerToken();
        long goodMaterial = material("true");
        long goodCustomer = customer(true);
        Map<String, Object> ok = valid(SpringType.COMPRESSION, code("SPR-R"));
        ok.put("primaryMaterialId", goodMaterial);
        ok.put("customerId", goodCustomer);
        send(post("/api/products"), token, ok).andExpect(status().isCreated()).andExpect(jsonPath("$.primaryMaterial.id").value(goodMaterial))
                .andExpect(jsonPath("$.customer.id").value(goodCustomer)).andExpect(jsonPath("$.primaryMaterial.code").isNotEmpty());

        for (Object[] bad : new Object[][] { { "primaryMaterialId", 999999999L }, { "primaryMaterialId", material("false") },
                { "customerId", 999999999L }, { "customerId", customer(false) } }) {
            Map<String, Object> body = valid(SpringType.COMPRESSION, code("SPR-R"));
            body.put((String) bad[0], bad[1]);
            send(post("/api/products"), token, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value(bad[0]));
        }
    }

    // ------------------------------------------------------------------------------------------- update and lifecycle

    @Test
    void updateReplacesFieldsAndSpecificationsAndChecksTheVersion() throws Exception {
        String token = engineerToken();
        JsonNode created = create(token, valid(SpringType.COMPRESSION, code("SPR-U")));
        long id = created.get("id").asLong();

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("name", "Renamed");
        change.put("wireDiameter", 3.0);
        change.put("specifications", new LinkedHashMap<>(Map.of("coilDirection", "LEFT")));
        send(put("/api/products/" + id), token, asUpdate(created, change)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.wireDiameter").value(3.0)).andExpect(jsonPath("$.specifications.coilDirection").value("LEFT"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.code").value(created.get("code").asText()));
        AuditCommand event = lastEvent("PRODUCT_UPDATED");
        assertThat(event.oldValue()).containsEntry("name", "Compression spring");
        assertThat(event.newValue()).containsEntry("name", "Renamed");
        assertThat(event.newValue().get("specifications")).isEqualTo(Map.of("coilDirection", "LEFT"));

        send(put("/api/products/" + id), token, asUpdate(created, change)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        Map<String, Object> noVersion = asUpdate(created, change);
        noVersion.remove("version");
        send(put("/api/products/" + id), token, noVersion).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        send(put("/api/products/999999999"), token, asUpdate(created, change)).andExpect(status().isNotFound());
    }

    @Test
    void theProductCodeCanNeverChange() throws Exception {
        String token = engineerToken();
        JsonNode created = create(token, valid(SpringType.COMPRESSION, code("SPR-C")));
        long id = created.get("id").asLong();
        send(put("/api/products/" + id), token, asUpdate(created, Map.of("productCode", "SPR-OTHER"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("productCode"));
        send(put("/api/products/" + id), token, asUpdate(created, Map.of("productCode", created.get("code").asText()))).andExpect(status().isOk()); // repeating it is fine
    }

    @Test
    void theSpringTypeMayChangeOnlyWhileTheProductIsADraft() throws Exception {
        String token = engineerToken();
        JsonNode draft = create(token, valid(SpringType.COMPRESSION, code("SPR-T")));
        Map<String, Object> toTorsion = new LinkedHashMap<>(valid(SpringType.TORSION, "x"));
        toTorsion.remove("productCode");
        toTorsion.put("version", draft.get("version").asLong());
        send(put("/api/products/" + draft.get("id").asLong()), token, toTorsion).andExpect(status().isOk())
                .andExpect(jsonPath("$.springType").value("TORSION")).andExpect(jsonPath("$.specifications.legAngle").value(90))
                .andExpect(jsonPath("$.specifications.coilDirection").value("LEFT"));

        long id = draft.get("id").asLong();
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk());
        JsonNode active = json.readTree(send(get("/api/products/" + id), token, null).andReturn().getResponse().getContentAsString());
        Map<String, Object> back = new LinkedHashMap<>(valid(SpringType.COMPRESSION, "x"));
        back.remove("productCode");
        back.put("version", active.get("version").asLong());
        send(put("/api/products/" + id), token, back).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("springType"));
    }

    @Test
    void editingAnActiveProductStillRequiresItsRequiredAttributes() throws Exception {
        String token = engineerToken();
        JsonNode created = create(token, valid(SpringType.TORSION, code("SPR-E")));
        long id = created.get("id").asLong();
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk());
        JsonNode active = json.readTree(send(get("/api/products/" + id), token, null).andReturn().getResponse().getContentAsString());

        Map<String, Object> dropsRequired = asUpdate(active, Map.of("specifications", new LinkedHashMap<>(Map.of("torque", 100))));
        send(put("/api/products/" + id), token, dropsRequired).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("specifications.legLength", "specifications.legAngle")));
        send(put("/api/products/" + id), token, asUpdate(active, Map.of("name", "Still active"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void statusChangesOnlyThroughTheLifecycleActionsAndObsoleteProductsAreLocked() throws Exception {
        String token = engineerToken();
        JsonNode created = create(token, valid(SpringType.COMPRESSION, code("SPR-L")));
        long id = created.get("id").asLong();
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk());
        long again = events.stream(AuditCommand.class).filter(e -> e.action().equals("PRODUCT_ACTIVATED")).count();
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk()); // already active: no new audit entry
        assertThat(events.stream(AuditCommand.class).filter(e -> e.action().equals("PRODUCT_ACTIVATED")).count()).isEqualTo(again);

        mockMvc.perform(Api.bearer(delete("/api/products/" + id).param("reason", "superseded"), token)).andExpect(status().isNoContent());
        JsonNode obsolete = json.readTree(send(get("/api/products/" + id), token, null).andExpect(jsonPath("$.status").value("OBSOLETE")).andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ?", Integer.class, id)).isEqualTo(1); // never deleted
        assertThat(lastEvent("PRODUCT_OBSOLETED").reason()).isEqualTo("superseded");

        send(delete("/api/products/" + id), token, null).andExpect(status().isNoContent()); // idempotent
        send(put("/api/products/" + id), token, asUpdate(obsolete, Map.of("name", "Edit obsolete"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));

        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT active FROM products WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void allowedActionsFollowTheStateAndThePermissionsOfTheCaller() throws Exception {
        String token = engineerToken();
        String productCode = code("SPR-A");
        long id = create(token, valid(SpringType.COMPRESSION, productCode)).get("id").asLong();
        send(get("/api/products/" + id), token, null).andExpect(jsonPath("$.allowedActions[0]").value("EDIT")).andExpect(jsonPath("$.allowedActions[1]").value("ACTIVATE"));
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(jsonPath("$.allowedActions[1]").value("OBSOLETE"));
        send(delete("/api/products/" + id), token, null).andExpect(status().isNoContent());
        send(get("/api/products/" + id), token, null).andExpect(jsonPath("$.allowedActions.length()").value(1)).andExpect(jsonPath("$.allowedActions[0]").value("ACTIVATE"));

        // the list carries the same actions, so a screen can offer row actions without loading each product
        send(get("/api/products?q=" + productCode), token, null).andExpect(jsonPath("$.content[0].allowedActions[0]").value("ACTIVATE"));

        // someone who may only view gets no actions at all
        String viewer = Api.login(mockMvc, json, testUsers.create("ADMIN").getUsername(), TestUsers.PASSWORD).accessToken();
        send(get("/api/products/" + id), viewer, null).andExpect(status().isOk()).andExpect(jsonPath("$.allowedActions.length()").value(0));
    }

    @Test
    void everyChangeIsAudited() throws Exception {
        String token = engineerToken();
        JsonNode created = create(token, valid(SpringType.EXTENSION, code("SPR-AU")));
        long id = created.get("id").asLong();
        send(put("/api/products/" + id), token, asUpdate(created, Map.of("name", "Changed"))).andExpect(status().isOk());
        send(post("/api/products/" + id + "/activate"), token, null).andExpect(status().isOk());
        send(delete("/api/products/" + id), token, null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity = 'Product' AND entity_id = ? ORDER BY id", String.class, String.valueOf(id)))
                .containsExactly("PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_ACTIVATED", "PRODUCT_OBSOLETED");
        assertThat(lastEvent("PRODUCT_CREATED").newValue().get("specifications").toString()).contains("hookType");
    }

    // ------------------------------------------------------------------------------------------- list

    @Test
    void listSearchesFiltersSortsAndPages() throws Exception {
        String token = engineerToken();
        String tag = "LP" + SEQ.incrementAndGet() + "X";
        long materialA = material("true");
        long customerA = customer(true);
        Map<String, Object> a = valid(SpringType.COMPRESSION, tag + "-A");
        a.put("name", "Alpha " + tag);
        a.put("primaryMaterialId", materialA);
        a.put("customerId", customerA);
        a.put("drawingNumber", "DRG-" + tag);
        Map<String, Object> b = valid(SpringType.TORSION, tag + "-B");
        b.put("name", "Beta " + tag);
        Map<String, Object> c = valid(SpringType.COMPRESSION, tag + "-C");
        c.put("name", "Gamma " + tag);
        create(token, a);
        create(token, b);
        long activated = create(token, c).get("id").asLong();
        send(post("/api/products/" + activated + "/activate"), token, null).andExpect(status().isOk());

        send(get("/api/products?q=" + tag), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].code").value(tag + "-A")).andExpect(jsonPath("$.content[0].primaryMaterial.id").value(materialA))
                .andExpect(jsonPath("$.content[0].customer.id").value(customerA)).andExpect(jsonPath("$.content[1].primaryMaterial").doesNotExist());
        send(get("/api/products?q=drg-" + tag.toLowerCase()), token, null).andExpect(jsonPath("$.totalElements").value(1)); // drawing number
        send(get("/api/products?q=" + tag + "&springType=TORSION"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/products?q=" + tag + "&status=ACTIVE"), token, null).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].code").value(tag + "-C"));
        send(get("/api/products?q=" + tag + "&status=DRAFT"), token, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/products?q=" + tag + "&materialId=" + materialA), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/products?q=" + tag + "&customerId=" + customerA), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/products?q=" + tag + "&active=false"), token, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/products?q=" + tag + "&sort=springType,desc&sort=code,asc"), token, null).andExpect(jsonPath("$.content[0].springType").value("TORSION"));
        send(get("/api/products?q=" + tag + "&sort=code,desc&size=2&page=1"), token, null).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPages").value(2));
        send(get("/api/products?sort=surfaceTreatment,asc"), token, null).andExpect(status().isBadRequest());
        send(get("/api/products?springType=ROPE"), token, null).andExpect(status().isBadRequest());
        send(get("/api/products/999999999"), token, null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------- cross-module

    @Test
    void aMaterialUsedByAnActiveProductCannotBeDeactivatedWithoutForce() throws Exception {
        String token = engineerToken();
        long materialId = material("true");
        Map<String, Object> body = valid(SpringType.COMPRESSION, code("SPR-M"));
        body.put("primaryMaterialId", materialId);
        long productId = create(token, body).get("id").asLong();
        String admin = Api.login(mockMvc, json, testUsers.create("ADMIN").getUsername(), TestUsers.PASSWORD).accessToken();

        send(delete("/api/materials/" + materialId), admin, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEACTIVATION_BLOCKED")).andExpect(jsonPath("$.detail").value(containsString("1 active product uses it")));
        send(delete("/api/products/" + productId), token, null).andExpect(status().isNoContent()); // obsolete products no longer block
        send(delete("/api/materials/" + materialId), admin, null).andExpect(status().isNoContent());
    }

    @Test
    void aProductMayKeepAMaterialOrCustomerThatLaterBecameInactive() throws Exception {
        String token = engineerToken();
        long materialId = material("true");
        long customerId = customer(true);
        Map<String, Object> body = valid(SpringType.COMPRESSION, code("SPR-K"));
        body.put("primaryMaterialId", materialId);
        body.put("customerId", customerId);
        JsonNode created = create(token, body);
        jdbc.update("UPDATE materials SET active = FALSE WHERE id = ?", materialId);
        jdbc.update("UPDATE customers SET active = FALSE, status = 'INACTIVE' WHERE id = ?", customerId);

        send(put("/api/products/" + created.get("id").asLong()), token, asUpdate(created, Map.of("name", "Kept references"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMaterial.id").value(materialId)).andExpect(jsonPath("$.customer.id").value(customerId));
    }

    @Test
    void anOperatorCallingProductEndpointsDirectlyGets403() throws Exception {
        // REQUIREMENTS 3.1: "an Operator calling PUT /api/products/{id} directly must receive HTTP 403"
        String engineer = engineerToken();
        JsonNode created = create(engineer, valid(SpringType.COMPRESSION, code("SPR-O")));
        String operator = Api.login(mockMvc, json, testUsers.create("OPERATOR").getUsername(), TestUsers.PASSWORD).accessToken();
        send(put("/api/products/" + created.get("id").asLong()), operator, asUpdate(created, Map.of("name", "Hacked"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        send(get("/api/products/" + created.get("id").asLong()), engineer, null).andExpect(jsonPath("$.name").value("Compression spring"));
        List<String> unchanged = new ArrayList<>(jdbc.queryForList("SELECT name FROM products WHERE id = ?", String.class, created.get("id").asLong()));
        assertThat(unchanged).containsExactly("Compression spring");
    }
}
