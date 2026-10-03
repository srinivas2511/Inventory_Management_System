package com.springmfg.ims.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.crm.service.CrmContactService;
import com.springmfg.ims.crm.service.CrmLeadService;
import com.springmfg.ims.crm.service.CrmOpportunityService;
import com.springmfg.ims.crm.service.CustomerTagService;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.UserRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.partner.repository.SupplierRepository;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Text-search regression tests against real PostgreSQL. A null string bound inside LOWER(CONCAT(...)) used to make
 * every list endpoint without a search term fail with "function lower(bytea) does not exist" (HTTP 500). Each list
 * endpoint is called with the search term absent, blank, matching and non-matching.
 */
@Transactional
class SearchIT extends AbstractIntegrationTest {

    @Autowired TestData data;
    @Autowired UserRepository userRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired SupplierRepository supplierRepo;
    @Autowired MaterialRepository materialRepo;
    @Autowired ProductRepository productRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired CrmLeadService leadService;
    @Autowired CrmContactService contactService;
    @Autowired CrmOpportunityService opportunityService;
    @Autowired CustomerTagService tagService;
    @Autowired ObjectMapper mapper;

    private ResultActions list(String path, String authority, String q) throws Exception {
        MockHttpServletRequestBuilder req = get(path)
                .with(user("search-it").authorities(new SimpleGrantedAuthority(authority)));
        // Other ITs leave committed rows behind, so the default page size (20) can hide the row under test.
        req = req.param("size", "1000");
        if (q != null) {
            req = req.param("q", q);
        }
        return mockMvc.perform(req);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Page responses carry the rows in "content"; the warehouse list is a bare array. */
    private static JsonNode rows(JsonNode root) {
        return root.has("content") ? root.get("content") : root;
    }

    private static boolean contains(JsonNode rows, String field, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.path(field).asText())) {
                return true;
            }
        }
        return false;
    }

    /** Runs the four search variants and asserts the created row is found by an absent, blank and matching term. */
    private void assertSearchable(String path, String authority, String codeField, String code, String matchingTerm,
                                  String missTerm) throws Exception {
        JsonNode absent = rows(body(list(path, authority, null).andExpect(status().isOk())));
        assertThat(contains(absent, codeField, code)).as("%s without q", path).isTrue();

        JsonNode blank = rows(body(list(path, authority, "").andExpect(status().isOk())));
        assertThat(contains(blank, codeField, code)).as("%s with blank q", path).isTrue();

        JsonNode spaces = rows(body(list(path, authority, "   ").andExpect(status().isOk())));
        assertThat(contains(spaces, codeField, code)).as("%s with whitespace q", path).isTrue();

        JsonNode hit = rows(body(list(path, authority, matchingTerm).andExpect(status().isOk())));
        assertThat(contains(hit, codeField, code)).as("%s with matching q", path).isTrue();

        JsonNode miss = rows(body(list(path, authority, missTerm).andExpect(status().isOk())));
        assertThat(contains(miss, codeField, code)).as("%s with non-matching q", path).isFalse();
    }

    @Test
    @DisplayName("customers list works without q, blank q, matching and non-matching q")
    void customers() throws Exception {
        Customer c = data.customer();
        assertSearchable("/api/customers", "MASTER_CUSTOMER_VIEW", "customerCode", c.getCustomerCode(),
                c.getCustomerCode().toLowerCase(Locale.ROOT), "zzz-no-such-customer");
    }

    @Test
    @DisplayName("suppliers list works without q, blank q, matching and non-matching q")
    void suppliers() throws Exception {
        Supplier s = data.supplier();
        assertSearchable("/api/suppliers", "MASTER_SUPPLIER_VIEW", "supplierCode", s.getSupplierCode(),
                s.getSupplierCode().toLowerCase(Locale.ROOT), "zzz-no-such-supplier");
    }

    @Test
    @DisplayName("materials list works without q, blank q, matching and non-matching q")
    void materials() throws Exception {
        Material m = data.material();
        assertSearchable("/api/materials", "MASTER_MATERIAL_VIEW", "materialCode", m.getMaterialCode(),
                m.getMaterialCode().toLowerCase(Locale.ROOT), "zzz-no-such-material");
    }

    @Test
    @DisplayName("products list works without q, blank q, matching and non-matching q")
    void products() throws Exception {
        Product p = data.product();
        assertSearchable("/api/products", "MASTER_PRODUCT_VIEW", "productCode", p.getProductCode(),
                p.getProductCode().toLowerCase(Locale.ROOT), "zzz-no-such-product");
    }

    @Test
    @DisplayName("warehouses list works without q, blank q, matching and non-matching q")
    void warehouses() throws Exception {
        Warehouse w = warehouseRepo.findAll().get(0);
        assertSearchable("/api/warehouses", "INVENTORY_VIEW", "warehouseCode", w.getWarehouseCode(),
                w.getWarehouseCode().toLowerCase(Locale.ROOT), "zzz-no-such-warehouse");
    }

    @Test
    @DisplayName("admin users list works without q, blank q, matching and non-matching q")
    void users() throws Exception {
        User u = new User();
        String username = "searchit" + data.code("U");
        u.setUsername(username);
        u.setFullName("Search IT User");
        u.setEmail(username + "@example.test");
        u.setPasswordHash("not-a-real-hash");
        u.setPasswordChangedAt(Instant.now());
        u.setActive(true);
        userRepo.save(u);
        assertSearchable("/api/admin/users", "IAM_USER_MANAGE", "username", username,
                username.toUpperCase(Locale.ROOT), "zzz-no-such-user");
    }

    @Test
    @DisplayName("repository searches accept a null term directly (no HTTP layer)")
    void repositoriesAcceptNull() {
        data.customer();
        data.supplier();
        data.material();
        data.product();
        var page = PageRequest.of(0, 50);
        assertThat(customerRepo.search(null, null, page).getContent()).isNotEmpty();
        assertThat(supplierRepo.search(null, null, page).getContent()).isNotEmpty();
        assertThat(materialRepo.search(null, null, null, page).getContent()).isNotEmpty();
        assertThat(productRepo.search(null, null, null, null, page).getContent()).isNotEmpty();
        assertThat(warehouseRepo.search(null, null)).isNotEmpty();
        assertThat(userRepo.search(null, null, page)).isNotNull();
    }

    @Test
    @DisplayName("CRM lead, contact, opportunity and customer-segment searches work without q")
    void crmSearches() {
        var page = PageRequest.of(0, 20);
        assertThat(leadService.search(null, null, null, page)).isNotNull();
        assertThat(leadService.search(null, null, "", page)).isNotNull();
        assertThat(leadService.search(null, null, "   ", page)).isNotNull();
        assertThat(contactService.search(null, null, null, page)).isNotNull();
        assertThat(contactService.search(null, "", null, page)).isNotNull();
        assertThat(opportunityService.search(null, null, null, null, page)).isNotNull();
        assertThat(opportunityService.search(null, null, null, "", page)).isNotNull();

        Customer c = data.customer();
        assertThat(tagService.searchCustomers(null, null, page).getContent()).isNotEmpty();
        assertThat(tagService.searchCustomers("", null, page).getContent()).isNotEmpty();
        assertThat(tagService.searchCustomers(c.getCustomerCode().toLowerCase(Locale.ROOT), null, page).getContent())
                .isNotEmpty();
    }
}
