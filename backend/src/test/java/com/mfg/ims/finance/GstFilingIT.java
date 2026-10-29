package com.mfg.ims.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.support.AbstractIntegrationTest;

@Transactional
class GstFilingIT extends AbstractIntegrationTest {

    // GF-1: GSTR-1 returns HTTP 200 with expected structure
    @Test
    @WithMockUser(authorities = "GST_REPORT_VIEW")
    void GF1_gstr1ReturnsPeriodAndLists() throws Exception {
        mockMvc.perform(get("/api/finance/gst/gstr1")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-04-01"))
                .andExpect(jsonPath("$.b2b").isArray())
                .andExpect(jsonPath("$.b2c").isArray())
                .andExpect(jsonPath("$.hsnSummary").isArray())
                .andExpect(jsonPath("$.docSummary").exists());
    }

    // GF-2: GSTR-3B returns HTTP 200 with tax liability fields
    @Test
    @WithMockUser(authorities = "GST_REPORT_VIEW")
    void GF2_gstr3bReturnsTaxLiability() throws Exception {
        mockMvc.perform(get("/api/finance/gst/gstr3b")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-04-01"))
                .andExpect(jsonPath("$.outwardTaxableTurnover").exists())
                .andExpect(jsonPath("$.outwardTaxLiability").exists())
                .andExpect(jsonPath("$.itcAvailable").exists())
                .andExpect(jsonPath("$.netPayable").exists());
    }

    // GF-3: E-way bill endpoint with missing invoice returns 404
    @Test
    @WithMockUser(authorities = "EWAY_BILL_CREATE")
    void GF3_ewayBillForMissingInvoiceReturns404() throws Exception {
        mockMvc.perform(post("/api/finance/gst/eway-bill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesInvoiceId\":999999}"))
                .andExpect(status().isNotFound());
    }

    // GF-4: Filing history returns list
    @Test
    @WithMockUser(authorities = "GST_REPORT_VIEW")
    void GF4_filingHistoryReturnsList() throws Exception {
        mockMvc.perform(get("/api/finance/gst/filing-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
