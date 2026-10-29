package com.mfg.ims.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.procurement.purchasereturnchallan.PurchaseReturnChallanService;
import com.mfg.ims.procurement.purchasereturnchallan.SaveChallanRequest;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

@Transactional
class PurchaseReturnChallanIT extends AbstractIntegrationTest {

    @Autowired PurchaseReturnChallanService service;
    @Autowired TestData td;

    private Long supplierId;
    private Long materialId;

    @BeforeEach
    void fixtures() {
        supplierId = td.supplier().getId();
        materialId = td.material().getId();
    }

    @Test
    @WithMockUser(authorities = {"PRC_CHALLAN_CREATE", "PRC_CHALLAN_VIEW"})
    void prc1_create_challan_generates_doc_no_and_persists_items() {
        var req = new SaveChallanRequest(supplierId, null, null, "KA01AB1234", "LR-001", "Test remarks",
                List.of(new SaveChallanRequest.ItemRequest(materialId, new BigDecimal("10.000"), "PCS", new BigDecimal("50.0000"))));

        var dto = service.create(req);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.docNo()).isNotBlank();
        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.supplierId()).isEqualTo(supplierId);
        assertThat(dto.vehicleNo()).isEqualTo("KA01AB1234");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).materialId()).isEqualTo(materialId);
        assertThat(dto.items().get(0).qty()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(dto.grandTotal()).isEqualByComparingTo(new BigDecimal("500.0000"));
    }

    @Test
    @WithMockUser(authorities = {"PRC_CHALLAN_CREATE", "PRC_CHALLAN_DISPATCH", "PRC_CHALLAN_VIEW"})
    void prc2_dispatch_changes_status_to_dispatched() {
        var req = new SaveChallanRequest(supplierId, null, null, null, null, null,
                List.of(new SaveChallanRequest.ItemRequest(materialId, new BigDecimal("5.000"), null, null)));
        var created = service.create(req);

        var dispatched = service.dispatch(created.id());

        assertThat(dispatched.status()).isEqualTo("DISPATCHED");
        assertThat(service.get(created.id()).status()).isEqualTo("DISPATCHED");
    }

    @Test
    @WithMockUser(authorities = {"PRC_CHALLAN_CREATE", "PRC_CHALLAN_DISPATCH"})
    void prc3_dispatch_already_dispatched_challan_throws_conflict() {
        var req = new SaveChallanRequest(supplierId, null, null, null, null, null,
                List.of(new SaveChallanRequest.ItemRequest(materialId, new BigDecimal("2.000"), null, null)));
        var created = service.create(req);
        service.dispatch(created.id());

        assertThatThrownBy(() -> service.dispatch(created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DISPATCHED");
    }
}
