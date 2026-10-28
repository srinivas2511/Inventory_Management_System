package com.mfg.ims.procurement.rfq;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RfqService {

    private final RfqRepository repo;

    public RfqService(RfqRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<RfqDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RfqDto get(Long id) {
        return toDto(find(id));
    }

    public RfqDto create(SaveRfqRequest req) {
        var rfq = new Rfq();
        rfq.setDocNo(req.docNo());
        rfq.setDueDate(req.dueDate());
        rfq.setDeliveryLocation(req.deliveryLocation());
        rfq.setNotes(req.notes());
        int lineNo = 1;
        for (var line : req.items()) {
            var item = new RfqItem();
            item.setRfq(rfq);
            item.setLineNo(lineNo++);
            item.setMaterialId(line.materialId());
            item.setProductId(line.productId());
            item.setDescription(line.description());
            item.setQty(line.qty());
            item.setUomId(line.uomId());
            item.setRequiredDate(line.requiredDate());
            rfq.getItems().add(item);
        }
        return toDto(repo.save(rfq));
    }

    public RfqDto send(Long id) {
        var rfq = find(id);
        if (!"DRAFT".equals(rfq.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "RFQ must be in DRAFT to send");
        }
        rfq.setStatus("SENT");
        return toDto(repo.save(rfq));
    }

    public RfqDto close(Long id) {
        var rfq = find(id);
        rfq.setStatus("CLOSED");
        return toDto(repo.save(rfq));
    }

    public RfqDto cancel(Long id) {
        var rfq = find(id);
        if ("CANCELLED".equals(rfq.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RFQ already cancelled");
        }
        rfq.setStatus("CANCELLED");
        return toDto(repo.save(rfq));
    }

    private Rfq find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RFQ not found: " + id));
    }

    private RfqDto toDto(Rfq rfq) {
        var items = rfq.getItems().stream()
                .map(i -> new RfqDto.RfqItemDto(i.getId(), i.getLineNo(), i.getMaterialId(), i.getProductId(),
                        i.getDescription(), i.getQty(), i.getUomId(), i.getRequiredDate()))
                .toList();
        return new RfqDto(rfq.getId(), rfq.getDocNo(), rfq.getStatus(), rfq.getDueDate(),
                rfq.getDeliveryLocation(), rfq.getNotes(), items);
    }
}
