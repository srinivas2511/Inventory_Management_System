package com.mfg.ims.procurement.purchasereturn;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;

@Service
@Transactional
public class PurchaseReturnService {

    private final PurchaseReturnRepository repo;
    private final InventoryPostingService postingService;
    private final JdbcTemplate jdbc;

    public PurchaseReturnService(PurchaseReturnRepository repo,
                                 InventoryPostingService postingService,
                                 JdbcTemplate jdbc) {
        this.repo = repo;
        this.postingService = postingService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReturnDto get(Long id) {
        return toDto(find(id));
    }

    public PurchaseReturnDto create(SavePurchaseReturnRequest req) {
        var ret = new PurchaseReturn();
        ret.setDocNo(req.docNo());
        ret.setSupplierId(req.supplierId());
        ret.setGrnId(req.grnId());
        ret.setInvoiceId(req.invoiceId());
        ret.setReasonCode(req.reasonCode());
        ret.setNotes(req.notes());

        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new PurchaseReturnItem();
                item.setPurchaseReturn(ret);
                item.setLineNo(lineNo++);
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUomId(line.uomId());
                item.setUnitPrice(line.unitPrice() != null ? line.unitPrice() : java.math.BigDecimal.ZERO);
                item.setBatchId(line.batchId());
                item.setLocationId(line.locationId());
                ret.getItems().add(item);
            }
        }

        return toDto(repo.save(ret));
    }

    public PurchaseReturnDto post(Long id) {
        var ret = find(id);
        if (!"DRAFT".equals(ret.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Return must be DRAFT to post");
        }

        for (var item : ret.getItems()) {
            Long warehouseId = resolveWarehouse(item.getLocationId());
            String itemType = item.getMaterialId() != null ? "MATERIAL" : "PRODUCT";
            var result = postingService.post(new PostingCommand(
                    "PURCHASE_RETURN", itemType,
                    item.getMaterialId(), item.getProductId(),
                    item.getBatchId(), null,
                    warehouseId, item.getLocationId(),
                    "AVAILABLE",
                    item.getQty(), null,
                    item.getUnitPrice(),
                    "PURCHASE_RETURN", ret.getId(), ret.getDocNo(),
                    "Return: " + ret.getDocNo()));
            item.setLedgerTxnId(result.transactionId());
        }

        ret.setStatus("POSTED");
        return toDto(repo.save(ret));
    }

    private Long resolveWarehouse(Long locationId) {
        Long wh = jdbc.queryForObject(
                "SELECT warehouse_id FROM ims.warehouse_locations WHERE id = ?",
                Long.class, locationId);
        if (wh == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Location not found: " + locationId);
        }
        return wh;
    }

    private PurchaseReturn find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Purchase return not found: " + id));
    }

    private PurchaseReturnDto toDto(PurchaseReturn ret) {
        var items = ret.getItems().stream()
                .map(i -> new PurchaseReturnDto.ItemDto(i.getId(), i.getLineNo(), i.getMaterialId(),
                        i.getProductId(), i.getDescription(), i.getQty(), i.getUomId(),
                        i.getUnitPrice(), i.getBatchId(), i.getLocationId()))
                .toList();
        return new PurchaseReturnDto(ret.getId(), ret.getDocNo(), ret.getStatus(), ret.getSupplierId(),
                ret.getGrnId(), ret.getInvoiceId(), ret.getReasonCode(), ret.getNotes(),
                ret.getDebitNoteId(), items);
    }
}
