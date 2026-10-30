package com.mfg.ims.production.serviceorder;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceOrderService {

    private final ServiceOrderRepository repo;
    private final DocumentNumberService docNos;
    private final ServiceConfirmationRepository confirmRepo;

    public ServiceOrderService(ServiceOrderRepository repo,
                                DocumentNumberService docNos,
                                ServiceConfirmationRepository confirmRepo) {
        this.repo = repo;
        this.docNos = docNos;
        this.confirmRepo = confirmRepo;
    }

    public ServiceOrderDto create(CreateServiceOrderRequest req, Long actorId) {
        ServiceOrder so = new ServiceOrder();
        so.setDocNo(docNos.next("SVO"));
        so.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        if (req.serviceKind() != null) so.setServiceKind(req.serviceKind());
        so.setVendorId(req.vendorId());
        so.setCustomerId(req.customerId());
        so.setDescription(req.description());
        so.setScheduledDate(req.scheduledDate());
        if (req.serviceCharge() != null) so.setServiceCharge(req.serviceCharge());
        if (req.taxAmount() != null) {
            so.setTaxAmount(req.taxAmount());
            BigDecimal charge = req.serviceCharge() != null ? req.serviceCharge() : BigDecimal.ZERO;
            so.setGrandTotal(charge.add(req.taxAmount()));
        }
        so.setRemarks(req.remarks());
        so.setCreatedBy(actorId);
        so = repo.save(so);
        return toDto(so);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderDto> list() {
        return repo.findByCompanyIdOrderByIdDesc(1L).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceOrderDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    public ServiceOrderDto complete(Long id, Long actorId) {
        ServiceOrder so = findOrThrow(id);
        so.setStatus("COMPLETED");
        so.setCompletedDate(LocalDate.now());
        so.setUpdatedBy(actorId);
        return toDto(repo.save(so));
    }

    /** Confirm the service delivery — creates a confirmation record and advances status to IN_PROGRESS. */
    public ServiceConfirmationDto confirm(Long id, ConfirmServiceRequest req, Long actorId) {
        ServiceOrder so = findOrThrow(id);
        ServiceConfirmation conf = new ServiceConfirmation();
        conf.setSoId(so.getId());
        conf.setConfirmedAt(Instant.now());
        conf.setConfirmedBy(actorId);
        if (req != null) conf.setRemarks(req.remarks());
        conf = confirmRepo.save(conf);
        // Advance status if still DRAFT or CONFIRMED
        if ("DRAFT".equals(so.getStatus()) || "CONFIRMED".equals(so.getStatus())) {
            so.setStatus("IN_PROGRESS");
            so.setUpdatedBy(actorId);
            repo.save(so);
        }
        return toConfirmDto(conf);
    }

    @Transactional(readOnly = true)
    public List<ServiceConfirmationDto> getConfirmations(Long id) {
        findOrThrow(id); // ensure exists
        return confirmRepo.findBySoIdOrderByConfirmedAtDesc(id).stream()
                .map(this::toConfirmDto).collect(Collectors.toList());
    }

    private ServiceOrder findOrThrow(Long id) {
        return repo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("ServiceOrder", id));
    }

    private ServiceOrderDto toDto(ServiceOrder so) {
        return new ServiceOrderDto(so.getId(), so.getDocNo(), so.getDocDate(), so.getStatus(),
                so.getServiceKind(), so.getVendorId(), so.getCustomerId(), so.getDescription(),
                so.getScheduledDate(), so.getCompletedDate(), so.getServiceCharge(),
                so.getTaxAmount(), so.getGrandTotal(), so.getRemarks(), so.getCreatedAt());
    }

    private ServiceConfirmationDto toConfirmDto(ServiceConfirmation c) {
        return new ServiceConfirmationDto(c.getId(), c.getSoId(), c.getConfirmedAt(),
                c.getConfirmedBy(), c.getRemarks());
    }
}
