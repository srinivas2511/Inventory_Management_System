package com.mfg.ims.sales.challan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.common.numbering.DocumentNumberService;

@Service
@Transactional
public class ChallanService {

    private final ChallanRepository repo;
    private final DocumentNumberService numberService;

    public ChallanService(ChallanRepository repo, DocumentNumberService numberService) {
        this.repo = repo;
        this.numberService = numberService;
    }

    @Transactional(readOnly = true)
    public List<ChallanDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ChallanDto get(Long id) {
        return toDto(find(id));
    }

    public ChallanDto create(SaveChallanRequest req) {
        var ch = new DeliveryChallan();
        ch.setDocNo(numberService.next("DC"));
        ch.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        ch.setCustomerId(req.customerId());
        ch.setChallanKind(req.challanKind() != null ? req.challanKind() : "DELIVERY");
        ch.setOcId(req.ocId());
        ch.setSalesOrderId(req.salesOrderId());
        ch.setDeliveryAddress(req.deliveryAddress());
        ch.setVehicleNo(req.vehicleNo());
        ch.setDriverName(req.driverName());
        ch.setLrNo(req.lrNo());
        ch.setCourier(req.courier());
        ch.setRemarks(req.remarks());
        // 11.4: returnable tracking
        if (req.isReturnable() != null) ch.setReturnable(req.isReturnable());
        ch.setExpectedReturnDate(req.expectedReturnDate());
        applyItems(ch, req);
        return toDto(repo.save(ch));
    }

    public ChallanDto issue(Long id) {
        var ch = find(id);
        if (!"DRAFT".equals(ch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Challan must be DRAFT to issue");
        }
        ch.setStatus("ISSUED");
        return toDto(repo.save(ch));
    }

    public ChallanDto cancel(Long id) {
        var ch = find(id);
        if ("CANCELLED".equals(ch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challan already cancelled");
        }
        ch.setStatus("CANCELLED");
        return toDto(repo.save(ch));
    }

    /**
     * 11.4: Mark a RETURNABLE challan as returned (sets returned_at = now()).
     */
    public ChallanDto markReturned(Long id) {
        var ch = find(id);
        if (!ch.isReturnable()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Challan is not marked as returnable");
        }
        if (!"ISSUED".equals(ch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only ISSUED challans can be marked as returned");
        }
        ch.setReturnedAt(Instant.now());
        ch.setStatus("RETURNED");
        return toDto(repo.save(ch));
    }

    private void applyItems(DeliveryChallan ch, SaveChallanRequest req) {
        if (req.items() == null) return;
        // 11.8: SERVICE_OUT kind does not move stock — just tracking
        // No inventory posting needed here; items are recorded for document purposes only.
        int lineNo = 1;
        for (var line : req.items()) {
            var item = new DeliveryChallanItem();
            item.setChallan(ch);
            item.setLineNo(lineNo++);
            item.setMaterialId(line.materialId());
            item.setProductId(line.productId());
            item.setDescription(line.description());
            item.setQty(line.qty());
            item.setUom(line.uom());
            item.setRate(line.rate());
            item.setBatchNo(line.batchNo());
            item.setOcLineId(line.ocLineId());
            if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
            ch.getItems().add(item);
        }
    }

    private DeliveryChallan find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Delivery challan not found: " + id));
    }

    private ChallanDto toDto(DeliveryChallan ch) {
        var items = ch.getItems().stream()
                .map(i -> new ChallanDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getLineTotal(), i.getBatchNo()))
                .toList();
        return new ChallanDto(ch.getId(), ch.getDocNo(), ch.getDocDate(), ch.getStatus(),
                ch.getChallanKind(), ch.getCustomerId(), ch.getOcId(), ch.getSalesOrderId(),
                ch.getDeliveryAddress(), ch.getVehicleNo(), ch.getDriverName(), ch.getLrNo(),
                ch.getCourier(), ch.getRemarks(), ch.getGrandTotal(),
                ch.isReturnable(), ch.getExpectedReturnDate(), ch.getReturnedAt(),
                items);
    }
}
