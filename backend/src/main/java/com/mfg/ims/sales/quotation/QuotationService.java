package com.mfg.ims.sales.quotation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.common.numbering.DocumentNumberService;

@Service
@Transactional
public class QuotationService {

    private final QuotationRepository repo;
    private final DocumentNumberService numberService;

    public QuotationService(QuotationRepository repo, DocumentNumberService numberService) {
        this.repo = repo;
        this.numberService = numberService;
    }

    @Transactional(readOnly = true)
    public List<QuotationDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuotationDto get(Long id) {
        return toDto(find(id));
    }

    public QuotationDto create(SaveQuotationRequest req) {
        var q = new Quotation();
        q.setDocNo(numberService.next("QT"));
        q.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        q.setCustomerId(req.customerId());
        q.setCurrency(req.currency() != null ? req.currency() : "INR");
        q.setValidUntil(req.validUntil());
        q.setDeliveryTerms(req.deliveryTerms());
        q.setEnquiryRef(req.enquiryRef());
        q.setRemarks(req.remarks());
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new QuotationItem();
                item.setQuotation(q);
                item.setLineNo(lineNo++);
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUom(line.uom());
                item.setRate(line.rate());
                if (line.discountPct() != null) item.setDiscountPct(line.discountPct());
                item.setLeadTimeDays(line.leadTimeDays());
                item.setCustomerPartNo(line.customerPartNo());
                if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
                q.getItems().add(item);
            }
        }
        return toDto(repo.save(q));
    }

    public QuotationDto update(Long id, SaveQuotationRequest req) {
        var q = find(id);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only DRAFT quotations can be updated");
        }
        q.setCustomerId(req.customerId());
        if (req.docDate() != null) q.setDocDate(req.docDate());
        q.setValidUntil(req.validUntil());
        q.setDeliveryTerms(req.deliveryTerms());
        q.setEnquiryRef(req.enquiryRef());
        q.setRemarks(req.remarks());
        q.getItems().clear();
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new QuotationItem();
                item.setQuotation(q);
                item.setLineNo(lineNo++);
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUom(line.uom());
                item.setRate(line.rate());
                if (line.discountPct() != null) item.setDiscountPct(line.discountPct());
                item.setLeadTimeDays(line.leadTimeDays());
                item.setCustomerPartNo(line.customerPartNo());
                if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
                q.getItems().add(item);
            }
        }
        return toDto(repo.save(q));
    }

    public QuotationDto submit(Long id) {
        var q = find(id);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Quotation must be DRAFT to submit");
        }
        q.setStatus("SUBMITTED");
        return toDto(repo.save(q));
    }

    public QuotationDto cancel(Long id, String reason) {
        var q = find(id);
        if ("CANCELLED".equals(q.getStatus()) || "CONVERTED".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation cannot be cancelled in status: " + q.getStatus());
        }
        q.setStatus("CANCELLED");
        q.setLostReason(reason);
        return toDto(repo.save(q));
    }

    public QuotationDto markLost(Long id, String reason) {
        var q = find(id);
        q.setStatus("LOST");
        q.setLostReason(reason);
        return toDto(repo.save(q));
    }

    /** Mark quotation CONVERTED after creating the downstream document. */
    public void markConverted(Long id, String targetType, Long targetId) {
        var q = find(id);
        q.setStatus("CONVERTED");
        q.setConvertedToType(targetType);
        q.setConvertedToId(targetId);
        repo.save(q);
    }

    private Quotation find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found: " + id));
    }

    private QuotationDto toDto(Quotation q) {
        var items = q.getItems().stream()
                .map(i -> new QuotationDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getLineTotal(),
                        i.getLeadTimeDays(), i.getCustomerPartNo()))
                .toList();
        return new QuotationDto(q.getId(), q.getDocNo(), q.getDocDate(), q.getStatus(),
                q.getRevisionNo(), q.getCustomerId(), q.getCurrency(), q.getValidUntil(),
                q.getGrandTotal(), q.getRemarks(), items);
    }
}
