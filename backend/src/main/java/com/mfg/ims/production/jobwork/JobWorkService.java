package com.mfg.ims.production.jobwork;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobWorkService {

    private final JobWorkOrderRepository repo;
    private final DocumentNumberService docNos;

    public JobWorkService(JobWorkOrderRepository repo, DocumentNumberService docNos) {
        this.repo = repo;
        this.docNos = docNos;
    }

    public JobWorkOrderDto create(CreateJobWorkOrderRequest req, Long actorId) {
        JobWorkOrder jwo = new JobWorkOrder();
        jwo.setDocNo(docNos.next("JWO"));
        jwo.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        jwo.setVendorId(req.vendorId());
        jwo.setChallanNo(req.challanNo());
        jwo.setIssueDate(req.issueDate());
        jwo.setExpectedReturn(req.expectedReturn());
        if (req.jobCharges() != null) jwo.setJobCharges(req.jobCharges());
        jwo.setRemarks(req.remarks());
        jwo.setCreatedBy(actorId);

        if (req.items() != null) {
            int line = 1;
            for (var item : req.items()) {
                JobWorkOrderItem i = new JobWorkOrderItem();
                i.setJwo(jwo);
                i.setLineNo(line++);
                i.setMaterialId(item.materialId());
                i.setProductId(item.productId());
                i.setDescription(item.description());
                i.setSentQty(item.sentQty());
                i.setUom(item.uom());
                jwo.getItems().add(i);
            }
        }

        jwo = repo.save(jwo);
        return toDto(jwo);
    }

    @Transactional(readOnly = true)
    public List<JobWorkOrderDto> list() {
        return repo.findByCompanyIdOrderByIdDesc(1L).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobWorkOrderDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    public JobWorkOrderDto issue(Long id, Long actorId) {
        JobWorkOrder jwo = findOrThrow(id);
        jwo.setStatus("ISSUED");
        jwo.setIssueDate(LocalDate.now());
        jwo.setUpdatedBy(actorId);
        return toDto(repo.save(jwo));
    }

    public JobWorkOrderDto receive(Long id, Long actorId) {
        JobWorkOrder jwo = findOrThrow(id);
        jwo.setStatus("RECEIVED");
        jwo.setActualReturn(LocalDate.now());
        jwo.setUpdatedBy(actorId);
        return toDto(repo.save(jwo));
    }

    private JobWorkOrder findOrThrow(Long id) {
        return repo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("JobWorkOrder", id));
    }

    private JobWorkOrderDto toDto(JobWorkOrder jwo) {
        List<JobWorkOrderItemDto> items = jwo.getItems().stream()
                .map(i -> new JobWorkOrderItemDto(i.getId(), i.getLineNo(), i.getMaterialId(),
                        i.getProductId(), i.getDescription(), i.getSentQty(),
                        i.getReceivedQty(), i.getScrappedQty(), i.getUom()))
                .collect(Collectors.toList());
        return new JobWorkOrderDto(jwo.getId(), jwo.getDocNo(), jwo.getDocDate(), jwo.getStatus(),
                jwo.getVendorId(), jwo.getChallanNo(), jwo.getIssueDate(),
                jwo.getExpectedReturn(), jwo.getActualReturn(), jwo.getJobCharges(),
                jwo.getScrapQty(), jwo.getRemarks(), jwo.getCreatedAt(), items);
    }
}
