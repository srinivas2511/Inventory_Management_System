package com.mfg.ims.sales.orderconfirmation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.sales.quotation.QuotationService;

@Service
@Transactional
public class OcService {

    private final OcRepository repo;
    private final DocumentNumberService numberService;
    private final QuotationService quotationService;

    public OcService(OcRepository repo, DocumentNumberService numberService,
            QuotationService quotationService) {
        this.repo = repo;
        this.numberService = numberService;
        this.quotationService = quotationService;
    }

    @Transactional(readOnly = true)
    public List<OcDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OcDto get(Long id) {
        return toDto(find(id));
    }

    public OcDto create(SaveOcRequest req) {
        var oc = new OrderConfirmation();
        oc.setDocNo(numberService.next("OC"));
        oc.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        oc.setCustomerId(req.customerId());
        oc.setOcKind(req.ocKind() != null ? req.ocKind() : "DOMESTIC");
        oc.setQuotationId(req.quotationId());
        oc.setCustomerPoNo(req.customerPoNo());
        oc.setCustomerPoDate(req.customerPoDate());
        oc.setDeliveryDate(req.deliveryDate());
        oc.setCurrency(req.currency() != null ? req.currency() : "INR");
        oc.setIncoterm(req.incoterm());
        oc.setRemarks(req.remarks());
        applyItems(oc, req);
        var saved = repo.save(oc);
        if (req.quotationId() != null) {
            quotationService.markConverted(req.quotationId(), "OC", saved.getId());
        }
        return toDto(saved);
    }

    public OcDto update(Long id, SaveOcRequest req) {
        var oc = find(id);
        if (!"DRAFT".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only DRAFT order confirmations can be updated");
        }
        oc.setCustomerId(req.customerId());
        if (req.docDate() != null) oc.setDocDate(req.docDate());
        oc.setCustomerPoNo(req.customerPoNo());
        oc.setCustomerPoDate(req.customerPoDate());
        oc.setDeliveryDate(req.deliveryDate());
        oc.setIncoterm(req.incoterm());
        oc.setRemarks(req.remarks());
        oc.getItems().clear();
        applyItems(oc, req);
        return toDto(repo.save(oc));
    }

    public OcDto confirm(Long id) {
        var oc = find(id);
        if (!"DRAFT".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OC must be DRAFT to confirm");
        }
        oc.setStatus("CONFIRMED");
        return toDto(repo.save(oc));
    }

    public OcDto cancel(Long id) {
        var oc = find(id);
        if ("CANCELLED".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OC already cancelled");
        }
        oc.setStatus("CANCELLED");
        return toDto(repo.save(oc));
    }

    private void applyItems(OrderConfirmation oc, SaveOcRequest req) {
        if (req.items() == null) return;
        int lineNo = 1;
        for (var line : req.items()) {
            var item = new OrderConfirmationItem();
            item.setOc(oc);
            item.setLineNo(lineNo++);
            item.setMaterialId(line.materialId());
            item.setProductId(line.productId());
            item.setDescription(line.description());
            item.setQty(line.qty());
            item.setUom(line.uom());
            item.setRate(line.rate());
            item.setDeliveryDate(line.deliveryDate());
            item.setCustomerPartNo(line.customerPartNo());
            if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
            oc.getItems().add(item);
        }
    }

    private OrderConfirmation find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order confirmation not found: " + id));
    }

    private OcDto toDto(OrderConfirmation oc) {
        var items = oc.getItems().stream()
                .map(i -> new OcDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getLineTotal(),
                        i.getDeliveryDate(), i.getCustomerPartNo(), i.getQtyDispatched()))
                .toList();
        return new OcDto(oc.getId(), oc.getDocNo(), oc.getDocDate(), oc.getStatus(),
                oc.getOcKind(), oc.getCustomerId(), oc.getQuotationId(),
                oc.getCustomerPoNo(), oc.getCustomerPoDate(), oc.getDeliveryDate(),
                oc.getCurrency(), oc.getGrandTotal(), oc.getRemarks(),
                oc.getSalesOrderId(), items);
    }
}
