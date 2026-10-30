package com.mfg.ims.production.jobwork;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.warehouse.domain.WarehouseLocation;
import com.mfg.ims.warehouse.repository.WarehouseLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobWorkService {

    private final JobWorkOrderRepository repo;
    private final DocumentNumberService docNos;
    private final JobWorkChargeRepository chargeRepo;
    private final InventoryPostingService postingService;
    private final WarehouseLocationRepository locationRepo;

    public JobWorkService(JobWorkOrderRepository repo,
                          DocumentNumberService docNos,
                          JobWorkChargeRepository chargeRepo,
                          InventoryPostingService postingService,
                          WarehouseLocationRepository locationRepo) {
        this.repo = repo;
        this.docNos = docNos;
        this.chargeRepo = chargeRepo;
        this.postingService = postingService;
        this.locationRepo = locationRepo;
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

    /** Issue specific materials to the sub-contractor via InventoryPostingService. */
    public JobWorkOrderDto issueMaterials(Long id, IssueMaterialsRequest req, Long actorId) {
        JobWorkOrder jwo = findOrThrow(id);
        if (req.lines() != null) {
            for (IssueMaterialsRequest.IssueLine line : req.lines()) {
                if (line.materialId() == null || line.qty() == null || line.locationId() == null) continue;
                WarehouseLocation loc = locationRepo.findById(line.locationId())
                        .orElseThrow(() -> new NotFoundException("Location", line.locationId()));
                Long warehouseId = loc.getWarehouse().getId();
                postingService.post(new PostingCommand(
                        "MATERIAL_ISSUE", "MATERIAL",
                        line.materialId(), null, null, null,
                        warehouseId, line.locationId(), "AVAILABLE",
                        line.qty(), null, null,
                        "JOB_WORK_ORDER", jwo.getId(), jwo.getDocNo(),
                        line.notes() != null ? line.notes() : "Job work issue to sub-contractor"));
            }
        }
        jwo.setStatus("ISSUED");
        jwo.setIssueDate(LocalDate.now());
        jwo.setUpdatedBy(actorId);
        return toDto(repo.save(jwo));
    }

    /** Receive goods back from sub-contractor. */
    public JobWorkOrderDto receive(Long id, ReceiveJobWorkRequest req, Long actorId) {
        JobWorkOrder jwo = findOrThrow(id);
        if (req != null) {
            // Update items with received qty
            if (req.receivedQty() != null && !jwo.getItems().isEmpty()) {
                jwo.getItems().get(0).setReceivedQty(req.receivedQty());
            }
            if (req.scrapQty() != null) {
                jwo.setScrapQty(req.scrapQty());
            }
        }
        jwo.setStatus("RECEIVED");
        jwo.setActualReturn(LocalDate.now());
        jwo.setUpdatedBy(actorId);
        return toDto(repo.save(jwo));
    }

    /** Record job work charges for later AP invoice matching. */
    public JobWorkChargeDto recordCharges(Long id, RecordChargesRequest req, Long actorId) {
        JobWorkOrder jwo = findOrThrow(id);
        JobWorkCharge charge = new JobWorkCharge();
        charge.setJwoId(jwo.getId());
        charge.setChargeDesc(req.chargeDescription());
        charge.setAmount(req.amount() != null ? req.amount() : BigDecimal.ZERO);
        charge.setChargeDate(req.chargeDate() != null ? req.chargeDate() : LocalDate.now());
        charge.setCreatedBy(actorId);
        charge = chargeRepo.save(charge);
        return toChargeDto(charge);
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

    private JobWorkChargeDto toChargeDto(JobWorkCharge c) {
        return new JobWorkChargeDto(c.getId(), c.getJwoId(), c.getChargeDesc(),
                c.getAmount(), c.getChargeDate(), c.getCreatedAt());
    }
}
