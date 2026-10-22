package com.mfg.ims.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * D-27: a customer or supplier could not be saved without a GST number, because the forms sent "" and the API's
 * pattern rejected it. GST is optional (DESIGN section 14 validates the format only); blank means "none" and is stored
 * as null, while a malformed value is still refused.
 */
@Transactional
class PartnerGstIT extends AbstractIntegrationTest {

    private static final String VALID_GST = "27AABCS1234A1Z5";

    @Autowired TestData data;
    @Autowired ObjectMapper mapper;

    private MvcResult create(String path, String authority, String codeField, String code, String gst) throws Exception {
        var actor = new CachedUser(9001L, "gst-it", true, 1, Set.of(), Set.of());
        var auth = new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(authority)));
        String gstPart = gst == null ? "" : ",\"gstNumber\":\"" + gst + "\"";
        String body = "{\"" + codeField + "\":\"" + code + "\",\"name\":\"GST IT partner\"" + gstPart + "}";
        return mockMvc.perform(post(path).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GST-1 a customer without a GST number (absent or blank) is created with no GST")
    void customerWithoutGstIsCreated() throws Exception {
        MvcResult absent = create("/api/customers", "CUSTOMER_MANAGE", "customerCode", data.code("GC"), null);
        assertThat(absent.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(absent).has("gstNumber")).as("no GST stored (null fields are omitted)").isFalse();

        MvcResult blank = create("/api/customers", "CUSTOMER_MANAGE", "customerCode", data.code("GC"), "");
        assertThat(blank.getResponse().getStatus()).as("the form sends an empty string").isEqualTo(201);
        assertThat(json(blank).has("gstNumber")).as("blank is stored as none").isFalse();
    }

    @Test
    @DisplayName("GST-2 a supplier without a GST number (absent or blank) is created with no GST")
    void supplierWithoutGstIsCreated() throws Exception {
        MvcResult absent = create("/api/suppliers", "SUPPLIER_MANAGE", "supplierCode", data.code("GS"), null);
        assertThat(absent.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(absent).has("gstNumber")).as("no GST stored (null fields are omitted)").isFalse();

        MvcResult blank = create("/api/suppliers", "SUPPLIER_MANAGE", "supplierCode", data.code("GS"), "");
        assertThat(blank.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(blank).has("gstNumber")).isFalse();
    }

    @Test
    @DisplayName("GST-3 a well-formed GST number is kept, a malformed one is still refused (customer and supplier)")
    void formatIsStillValidated() throws Exception {
        MvcResult customer = create("/api/customers", "CUSTOMER_MANAGE", "customerCode", data.code("GC"), VALID_GST);
        assertThat(customer.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(customer).path("gstNumber").asText()).isEqualTo(VALID_GST);

        MvcResult supplier = create("/api/suppliers", "SUPPLIER_MANAGE", "supplierCode", data.code("GS"), VALID_GST);
        assertThat(supplier.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(supplier).path("gstNumber").asText()).isEqualTo(VALID_GST);

        MvcResult badCustomer = create("/api/customers", "CUSTOMER_MANAGE", "customerCode", data.code("GC"), "NOT-A-GST");
        assertThat(badCustomer.getResponse().getStatus()).isEqualTo(400);
        assertThat(badCustomer.getResponse().getContentAsString()).contains("Invalid GST number format");

        MvcResult badSupplier = create("/api/suppliers", "SUPPLIER_MANAGE", "supplierCode", data.code("GS"), "27AABCS1234A1Z");
        assertThat(badSupplier.getResponse().getStatus()).isEqualTo(400);
    }
}
