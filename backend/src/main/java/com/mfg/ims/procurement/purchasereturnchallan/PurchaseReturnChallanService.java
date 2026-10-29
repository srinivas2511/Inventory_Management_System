package com.mfg.ims.procurement.purchasereturnchallan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;

@Service
@Transactional
public class PurchaseReturnChallanService {

    private static final Long COMPANY_ID = 1L;

    private final PurchaseReturnChallanRepository repo;
    private final DocumentNumberService numberService;

    public PurchaseReturnChallanService(PurchaseReturnChallanRepository repo,
                                        DocumentNumberService numberService) {
        this.repo = repo;
        this.numberService = numberService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnChallanDto> list() {
        return repo.findByCompanyIdOrderByDocDateDescIdDesc(COMPANY_ID)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReturnChallanDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    public PurchaseReturnChallanDto create(SaveChallanRequest req) {
        var ch = new PurchaseReturnChallan();
        ch.setDocNo(numberService.next("PRC"));
        ch.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        ch.setSupplierId(req.supplierId());
        ch.setPurchaseReturnId(req.purchaseReturnId());
        ch.setVehicleNo(req.vehicleNo());
        ch.setLrNo(req.lrNo());
        ch.setRemarks(req.remarks());
        applyItems(ch, req);
        ch.setGrandTotal(computeTotal(ch));
        return toDto(repo.save(ch));
    }

    public PurchaseReturnChallanDto dispatch(Long id) {
        var ch = findOrThrow(id);
        if (!"DRAFT".equals(ch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Challan is already " + ch.getStatus());
        }
        ch.setStatus("DISPATCHED");
        return toDto(repo.save(ch));
    }

    private void applyItems(PurchaseReturnChallan ch, SaveChallanRequest req) {
        ch.getItems().clear();
        int line = 1;
        for (var r : req.items()) {
            var item = new PurchaseReturnChallanItem();
            item.setChallan(ch);
            item.setLineNo(line++);
            item.setMaterialId(r.materialId());
            item.setQty(r.qty());
            item.setUom(r.uom() != null ? r.uom() : "PCS");
            item.setUnitPrice(r.unitPrice() != null ? r.unitPrice() : BigDecimal.ZERO);
            ch.getItems().add(item);
        }
    }

    private BigDecimal computeTotal(PurchaseReturnChallan ch) {
        return ch.getItems().stream()
                .map(i -> i.getQty().multiply(i.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PurchaseReturnChallan findOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase-return challan not found: " + id));
    }

    private PurchaseReturnChallanDto toDto(PurchaseReturnChallan ch) {
        var items = ch.getItems().stream()
                .map(i -> new PurchaseReturnChallanDto.ItemDto(
                        i.getId(), i.getLineNo(), i.getMaterialId(),
                        i.getQty(), i.getUom(), i.getUnitPrice()))
                .toList();
        return new PurchaseReturnChallanDto(
                ch.getId(), ch.getDocNo(), ch.getDocDate(), ch.getStatus(),
                ch.getSupplierId(), ch.getPurchaseReturnId(),
                ch.getVehicleNo(), ch.getLrNo(), ch.getRemarks(),
                ch.getGrandTotal(), items);
    }
}
