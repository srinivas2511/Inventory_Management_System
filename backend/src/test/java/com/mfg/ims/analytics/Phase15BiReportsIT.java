package com.mfg.ims.analytics;

import com.mfg.ims.analytics.bi.BiService;
import com.mfg.ims.analytics.register.RegisterService;
import com.mfg.ims.analytics.reportbuilder.CreateSavedReportRequest;
import com.mfg.ims.analytics.reportbuilder.ReportBuilderService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 — BI, registers and report builder.
 *
 * <p>P15-1 List BI areas; P15-2 Get BI area; P15-3 List registers; P15-4 Get register;
 * P15-5 Create and list saved reports.
 */
@Transactional
class Phase15BiReportsIT extends AbstractIntegrationTest {

    @Autowired BiService biService;
    @Autowired RegisterService registerService;
    @Autowired ReportBuilderService reportBuilderService;

    private static final Long TEST_USER_ID = 9015L;

    @BeforeEach
    void setUpSecurityContext() {
        var actor = new CachedUser(TEST_USER_ID, "p15-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("BI_VIEW", "BI_SALES", "BI_PURCHASE", "BI_INVENTORY", "BI_PRODUCTION",
                        "REPORT_BUILD", "REPORT_RUN", "REGISTER_VIEW"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(
                        new SimpleGrantedAuthority("BI_VIEW"),
                        new SimpleGrantedAuthority("REPORT_BUILD"),
                        new SimpleGrantedAuthority("REGISTER_VIEW"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void p15_1_list_bi_areas() {
        var areas = biService.listAreas();
        assertThat(areas).contains("sales", "purchase", "inventory", "production");
    }

    @Test
    void p15_2_get_bi_area() {
        var dto = biService.getArea("sales");
        assertThat(dto.area()).isEqualTo("sales");
        assertThat(dto.totalCount()).isGreaterThanOrEqualTo(0);
        assertThat(dto.totalAmount()).isNotNull();
    }

    @Test
    void p15_3_list_registers() {
        var codes = registerService.listCodes();
        assertThat(codes).contains("REG_SALES_INVOICE", "REG_PURCHASE_ORDER", "REG_PAYMENT");
    }

    @Test
    void p15_4_get_register() {
        var reg = registerService.get("REG_PAYMENT");
        assertThat(reg.code()).isEqualTo("REG_PAYMENT");
        assertThat(reg.name()).isNotBlank();
        assertThat(reg.rows()).isNotNull();
    }

    @Test
    void p15_6_additional_registers_present() {
        var codes = registerService.listCodes();
        assertThat(codes).contains(
                "REG_QUOTATION", "REG_PO_ITEMS", "REG_SO_ITEMS",
                "REG_INVOICE_ITEMS", "REG_ADJUSTMENT_NOTES",
                "REG_LAST_PRICE", "REG_PRODUCTION_SUMMARY", "REG_PO_BALANCE");
    }

    @Test
    void p15_7_additional_registers_return_rows() {
        for (var code : List.of("REG_QUOTATION", "REG_PO_ITEMS", "REG_SO_ITEMS",
                "REG_INVOICE_ITEMS", "REG_ADJUSTMENT_NOTES",
                "REG_LAST_PRICE", "REG_PRODUCTION_SUMMARY", "REG_PO_BALANCE")) {
            var reg = registerService.get(code);
            assertThat(reg.code()).isEqualTo(code);
            assertThat(reg.name()).isNotBlank();
            assertThat(reg.rows()).isNotNull();
        }
    }

    @Test
    void p15_5_save_and_list_report() {
        var req = new CreateSavedReportRequest("Test Sales Report", "bi_sales",
                Map.of("fields", List.of("doc_no", "grand_total")), "PRIVATE");

        var saved = reportBuilderService.createSavedReport(req, TEST_USER_ID);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("Test Sales Report");
        assertThat(saved.sourceView()).isEqualTo("bi_sales");

        var list = reportBuilderService.listSavedReports();
        assertThat(list).anyMatch(r -> r.id().equals(saved.id()));
    }
}
