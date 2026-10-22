package com.mfg.ims.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.procurement.domain.PurchaseRequisition;
import com.mfg.ims.procurement.repository.PurchaseRequisitionRepository;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * D-32: the requisition list answered empty by default because a null status went to the derived finder
 * findByStatus(null), which PostgreSQL evaluates as "status IS NULL". A missing or blank status means "no filter".
 */
@Transactional
class RequisitionListIT extends AbstractIntegrationTest {

    @Autowired TestData data;
    @Autowired PurchaseRequisitionRepository prRepo;
    @Autowired ObjectMapper mapper;

    private PurchaseRequisition requisition(Material material, String status) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNumber(data.code("PR"));
        pr.setMaterialId(material.getId());
        pr.setRequestedQty(new BigDecimal("10.000"));
        pr.setUom("KG");
        pr.setStatus(status);
        pr.setRequestedBy(1L);
        return prRepo.save(pr);
    }

    private JsonNode list(String status) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/procurement/requisitions")
                .with(user("pr-it").authorities(new SimpleGrantedAuthority("PURCHASE_VIEW")))
                .param("size", "1000");
        if (status != null) {
            req = req.param("status", status);
        }
        String json = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("content");
    }

    private static boolean contains(JsonNode rows, String prNumber) {
        for (JsonNode row : rows) {
            if (prNumber.equals(row.path("prNumber").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("REQ-LIST-1 a list without a status filter returns every requisition, whatever its status")
    void noStatusReturnsEverything() throws Exception {
        Material material = data.material();
        PurchaseRequisition open = requisition(material, "OPEN");
        PurchaseRequisition cancelled = requisition(material, "CANCELLED");

        JsonNode rows = list(null);
        assertThat(contains(rows, open.getPrNumber())).as("the open requisition is listed by default").isTrue();
        assertThat(contains(rows, cancelled.getPrNumber())).as("the cancelled requisition is listed too").isTrue();
    }

    @Test
    @DisplayName("REQ-LIST-2 a blank status is no filter, a real status still filters")
    void blankStatusIsNoFilterAndRealStatusFilters() throws Exception {
        Material material = data.material();
        PurchaseRequisition open = requisition(material, "OPEN");
        PurchaseRequisition cancelled = requisition(material, "CANCELLED");

        assertThat(contains(list(""), open.getPrNumber())).isTrue();
        assertThat(contains(list("  "), cancelled.getPrNumber())).isTrue();

        JsonNode onlyOpen = list("OPEN");
        assertThat(contains(onlyOpen, open.getPrNumber())).isTrue();
        assertThat(contains(onlyOpen, cancelled.getPrNumber())).as("the status filter still excludes others").isFalse();
    }
}
