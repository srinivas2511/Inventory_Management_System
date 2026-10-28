package com.mfg.ims.production;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.production.barcode.BarcodeService;
import com.mfg.ims.production.barcode.GenerateBarcodeRequest;
import com.mfg.ims.production.costrollup.CostRollupService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13 — Barcodes and cost roll-up. (MRP is covered by existing MrpIT in the mrp package.)
 *
 * <p>P13-1 Generate barcode; P13-2 List barcodes; P13-3 Record barcode print;
 * P13-4 Run cost roll-up; P13-5 List cost roll-up log.
 */
@Transactional
class Phase13ManufacturingIT extends AbstractIntegrationTest {

    @Autowired BarcodeService barcodeService;
    @Autowired CostRollupService costRollupService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9013L;

    @BeforeEach
    void setUpSecurityContext() {
        td.material(); // ensure at least one material exists for cost rollup
        var actor = new CachedUser(TEST_USER_ID, "p13-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("BARCODE_VIEW", "BARCODE_MANAGE", "COST_ROLLUP_VIEW", "COST_ROLLUP_RUN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(
                        new SimpleGrantedAuthority("BARCODE_VIEW"),
                        new SimpleGrantedAuthority("BARCODE_MANAGE"),
                        new SimpleGrantedAuthority("COST_ROLLUP_VIEW"),
                        new SimpleGrantedAuthority("COST_ROLLUP_RUN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void p13_1_generate_barcode() {
        var mat = td.material();
        var req = new GenerateBarcodeRequest("MATERIAL", "MATERIAL", mat.getId(), null);

        var label = barcodeService.generate(req, TEST_USER_ID);

        assertThat(label.id()).isNotNull();
        assertThat(label.labelType()).isEqualTo("MATERIAL");
        assertThat(label.refId()).isEqualTo(mat.getId());
        assertThat(label.barcodeValue()).isNotBlank();
        assertThat(label.printCount()).isZero();
    }

    @Test
    void p13_2_list_barcodes() {
        var mat = td.material();
        barcodeService.generate(new GenerateBarcodeRequest("MATERIAL", "MATERIAL", mat.getId(), null), TEST_USER_ID);

        var labels = barcodeService.list();

        assertThat(labels).isNotEmpty();
    }

    @Test
    void p13_3_record_barcode_print() {
        var mat = td.material();
        var generated = barcodeService.generate(
                new GenerateBarcodeRequest("MATERIAL", "MATERIAL", mat.getId(), null), TEST_USER_ID);

        var printed = barcodeService.recordPrint(generated.id(), TEST_USER_ID);

        assertThat(printed.printCount()).isEqualTo(1);
        assertThat(printed.printedAt()).isNotNull();
    }

    @Test
    void p13_4_cost_rollup_run() {
        var logs = costRollupService.runRollup(TEST_USER_ID);

        assertThat(logs).isNotEmpty();
        logs.forEach(log -> {
            assertThat(log.materialId()).isNotNull();
            assertThat(log.rolledCost()).isNotNull();
            assertThat(log.rolledAt()).isNotNull();
        });
    }

    @Test
    void p13_5_list_cost_rollup() {
        costRollupService.runRollup(TEST_USER_ID);

        var logs = costRollupService.list();

        assertThat(logs).isNotEmpty();
    }
}
