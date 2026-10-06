package com.springmfg.ims.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.procurement.domain.PurchaseOrder;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import org.junit.jupiter.api.BeforeEach;

@Transactional
class ProcurementIT extends AbstractIntegrationTest {

    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired MaterialBatchRepository batchRepo;
    @Autowired QualityInspectionRepository inspectionRepo;

    @Autowired TestData td;

    // Warehouse 1 / location 1 are seeded by migration V2; supplier and material are created per test.
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID  = 1L;
    private Long SUPPLIER_ID;
    private Long MATERIAL_ID;

    @BeforeEach
    void fixtures() {
        SUPPLIER_ID = td.supplier().getId();
        MATERIAL_ID = td.material().getId();
    }

    @Test
    @WithMockUser(authorities = {"PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE"})
    void goodsReceiptList_filtersByStatusPoNumberAndDateAndNamesThePo() {
        var po = poService.create(new CreatePoRequest(
                SUPPLIER_ID, LocalDate.now(), LocalDate.now().plusDays(7), "NET30", null, null,
                List.of(new CreatePoRequest.PoLineRequest(
                        MATERIAL_ID, new BigDecimal("100.000"), new BigDecimal("50.0000"), new BigDecimal("18.00")))));
        po = poService.submit(po.id());
        PurchaseOrder entity = poRepo.findById(po.id()).orElseThrow();
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);
        po = poService.approve(po.id());
        var gr = grService.receive(new CreateGrRequest(
                po.id(), LocalDate.now(), WAREHOUSE_ID, "CHALLAN-LIST", "MH01AB9999",
                List.of(new CreateGrRequest.GrLineRequest(
                        po.items().get(0).id(), new BigDecimal("10.000"), LOCATION_ID, "HEAT-L", "CERT-L", null, true))));
        // Newest first, as the endpoint defaults: other tests in the shared database leave more than a page of receipts.
        var page = org.springframework.data.domain.PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

        var all = grService.search(null, null, null, null, null, page);
        assertThat(all.getContent()).extracting(g -> g.id()).contains(gr.id());
        assertThat(all.getContent().stream().filter(g -> g.id().equals(gr.id())).findFirst().orElseThrow().poNumber())
                .isEqualTo(po.poNumber());

        assertThat(grService.search("POSTED", po.id(), null, null, null, page).getContent())
                .extracting(g -> g.id()).containsExactly(gr.id());
        assertThat(grService.search("CANCELLED", po.id(), null, null, null, page).getContent()).isEmpty();
        assertThat(grService.search(null, null, po.poNumber().toLowerCase(), null, null, page).getContent())
                .extracting(g -> g.id()).contains(gr.id());
        assertThat(grService.search(null, null, "no-such-po", null, null, page).getContent()).isEmpty();
        assertThat(grService.search(null, po.id(), null, LocalDate.now().plusDays(1), null, page).getContent()).isEmpty();
        assertThat(grService.search(null, po.id(), null, null, LocalDate.now().minusDays(1), page).getContent()).isEmpty();
        assertThat(grService.search(null, po.id(), "  ", LocalDate.now(), LocalDate.now(), page).getContent())
                .extracting(g -> g.id()).containsExactly(gr.id());
    }

    @Test
    @WithMockUser(authorities = {"PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE"})
    void fullProcurementFlow_draftToPo_grCreatesQuarantineStock() {
        // Create PO
        CreatePoRequest poReq = new CreatePoRequest(
                SUPPLIER_ID, LocalDate.now(), LocalDate.now().plusDays(7),
                "NET30", null, null,
                List.of(new CreatePoRequest.PoLineRequest(
                        MATERIAL_ID, new BigDecimal("100.000"),
                        new BigDecimal("50.0000"), new BigDecimal("18.00"))));

        var po = poService.create(poReq);
        assertThat(po.status()).isEqualTo("DRAFT");

        // Submit
        po = poService.submit(po.id());
        assertThat(po.status()).isEqualTo("SUBMITTED");

        // Override submittedBy so approve doesn't fail segregation check
        PurchaseOrder entity = poRepo.findById(po.id()).orElseThrow();
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);

        po = poService.approve(po.id());
        assertThat(po.status()).isEqualTo("APPROVED");

        // Receive goods
        CreateGrRequest grReq = new CreateGrRequest(
                po.id(), LocalDate.now(), WAREHOUSE_ID,
                "CHALLAN-001", "MH01AB1234",
                List.of(new CreateGrRequest.GrLineRequest(
                        po.items().get(0).id(),
                        new BigDecimal("50.000"),
                        LOCATION_ID,
                        "HEAT-001", "CERT-001", null, true)));

        var gr = grService.receive(grReq);
        assertThat(gr.grNumber()).startsWith("GR/");
        assertThat(gr.items()).hasSize(1);

        // Batch should exist with QUARANTINE status
        var batch = batchRepo.findById(gr.items().get(0).materialBatchId());
        assertThat(batch).isPresent();
        assertThat(batch.get().getStatus()).isEqualTo("QUARANTINE");

        // PO should now be PARTIALLY_RECEIVED
        var updatedPo = poRepo.findById(po.id()).orElseThrow();
        assertThat(updatedPo.getStatus()).isEqualTo("PARTIALLY_RECEIVED");

        // Inspection should have been created
        var inspOpt = inspectionRepo.findByMaterialBatchId(gr.items().get(0).materialBatchId());
        assertThat(inspOpt).isPresent();
        assertThat(inspOpt.get().getStatus()).isEqualTo("PENDING");
    }

    @Test
    @WithMockUser(authorities = {"PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE"})
    void overReceipt_exceedingTolerance_throwsBusinessRuleException() {
        CreatePoRequest poReq = new CreatePoRequest(
                SUPPLIER_ID, LocalDate.now(), null, null, null, null,
                List.of(new CreatePoRequest.PoLineRequest(
                        MATERIAL_ID, new BigDecimal("10.000"),
                        new BigDecimal("100.0000"), BigDecimal.ZERO)));

        var po = poService.create(poReq);
        PurchaseOrder entity = poRepo.findById(po.id()).orElseThrow();
        entity.setStatus("APPROVED");
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);

        // Try to receive 12 against 10 ordered (20% > 10% tolerance)
        CreateGrRequest grReq = new CreateGrRequest(
                po.id(), LocalDate.now(), WAREHOUSE_ID, null, null,
                List.of(new CreateGrRequest.GrLineRequest(
                        po.items().get(0).id(),
                        new BigDecimal("12.000"),
                        LOCATION_ID,
                        null, null, null, false)));

        assertThatThrownBy(() -> grService.receive(grReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
    }
}
