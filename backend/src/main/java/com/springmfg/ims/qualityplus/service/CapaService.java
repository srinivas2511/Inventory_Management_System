package com.springmfg.ims.qualityplus.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.quality.domain.Rejection;
import com.springmfg.ims.quality.domain.RejectionReason;
import com.springmfg.ims.quality.repository.RejectionReasonRepository;
import com.springmfg.ims.quality.repository.RejectionRepository;
import com.springmfg.ims.qualityplus.domain.CapaRecord;
import com.springmfg.ims.qualityplus.domain.CapaSourceType;
import com.springmfg.ims.qualityplus.domain.CapaStatus;
import com.springmfg.ims.qualityplus.domain.CustomerComplaint;
import com.springmfg.ims.qualityplus.domain.SpcDataPoint;
import com.springmfg.ims.qualityplus.dto.CapaDtos.AdvanceCapaRequest;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CapaDto;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CapaSummaryDto;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CloseCapaRequest;
import com.springmfg.ims.qualityplus.dto.CapaDtos.SaveCapaRequest;
import com.springmfg.ims.qualityplus.repository.CapaRecordRepository;
import com.springmfg.ims.qualityplus.repository.CustomerComplaintRepository;

/**
 * Corrective and preventive actions (QMS-009). A CAPA moves one step at a time; moving into action needs a root
 * cause, moving into verification needs a corrective action, and closing is a separate step by a different
 * authority that records who verified the fix and why it is effective. Every step is audited.
 */
@Service
public class CapaService {

    private final CapaRecordRepository repo;
    private final CustomerComplaintRepository complaintRepo;
    private final RejectionRepository rejectionRepo;
    private final RejectionReasonRepository reasonRepo;
    private final SpcService spcService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public CapaService(CapaRecordRepository repo, CustomerComplaintRepository complaintRepo,
                       RejectionRepository rejectionRepo, RejectionReasonRepository reasonRepo,
                       SpcService spcService, DocumentNumberService numberService, AuditService auditService) {
        this.repo = repo;
        this.complaintRepo = complaintRepo;
        this.rejectionRepo = rejectionRepo;
        this.reasonRepo = reasonRepo;
        this.spcService = spcService;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CapaSummaryDto> search(String q, CapaStatus status, CapaSourceType source, Pageable pageable) {
        return repo.search(q, status == null ? null : status.name(), source == null ? null : source.name(), pageable)
                .map(c -> new CapaSummaryDto(c.getId(), c.getCapaNumber(), c.getTitle(), c.getSourceType(),
                        c.getSourceId(), c.getStatus(), c.getTargetCompletionDate(), c.getAssignedTo(),
                        c.getOpenedAt(), c.getClosedAt()));
    }

    @Transactional(readOnly = true)
    public CapaDto get(Long id) {
        return toDto(load(id));
    }

    // ---- raising ---------------------------------------------------------------------------------------------

    @Transactional
    public CapaDto create(SaveCapaRequest req) {
        validateSource(req.sourceType(), req.sourceId());
        CapaRecord capa = raise(req.title().trim(), req.description(), req.sourceType(), req.sourceId());
        copyEditable(capa, req);
        return toDto(finishCreate(capa));
    }

    /** "CAPA created from rejection event" (gate M16). One open CAPA per rejection. */
    @Transactional
    public CapaDto fromRejection(Long rejectionId) {
        Rejection r = rejectionRepo.findById(rejectionId)
                .orElseThrow(() -> new NotFoundException("Rejection not found: " + rejectionId));
        rejectOpenDuplicate(CapaSourceType.REJECTION_ANALYSIS, rejectionId);
        String reason = r.getRejectionReasonId() == null ? "no reason recorded"
                : reasonRepo.findById(r.getRejectionReasonId()).map(RejectionReason::getName).orElse("unknown reason");
        CapaRecord capa = raise("Rejection " + rejectionId + ": " + reason,
                "Rejected quantity " + r.getQuantity() + " (" + r.getItemType() + "), disposition "
                        + r.getDisposition() + ". Reason: " + reason + ".",
                CapaSourceType.REJECTION_ANALYSIS, rejectionId);
        return toDto(finishCreate(capa));
    }

    @Transactional
    public CapaDto fromSpcPoint(Long pointId) {
        SpcDataPoint point = spcService.requireOutOfControlPoint(pointId);
        rejectOpenDuplicate(CapaSourceType.SPC_VIOLATION, pointId);
        String where = spcService.describePoint(point);
        CapaRecord capa = raise("SPC violation: " + where, "Out-of-control signal on the control chart: " + where,
                CapaSourceType.SPC_VIOLATION, pointId);
        return toDto(finishCreate(capa));
    }

    /** Raises a CAPA for a complaint and links the two. A complaint has at most one CAPA. */
    @Transactional
    public CapaDto fromComplaint(Long complaintId) {
        CustomerComplaint complaint = complaintRepo.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found: " + complaintId));
        if (complaint.getCapaId() != null) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Complaint " + complaint.getComplaintNumber() + " already has a CAPA");
        }
        CapaRecord capa = raise("Complaint " + complaint.getComplaintNumber(), complaint.getDescription(),
                CapaSourceType.CUSTOMER_COMPLAINT, complaintId);
        capa = finishCreate(capa);
        complaint.setCapaId(capa.getId());
        complaintRepo.save(complaint);
        auditService.record("COMPLAINT_CAPA_LINKED", "CustomerComplaint", String.valueOf(complaintId), null,
                capa.getCapaNumber(), null);
        return toDto(capa);
    }

    // ---- working a CAPA -------------------------------------------------------------------------------------------

    @Transactional
    public CapaDto update(Long id, SaveCapaRequest req) {
        CapaRecord capa = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("CAPA not found: " + id));
        if (capa.getStatus() == CapaStatus.CLOSED) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "A closed CAPA cannot be edited");
        }
        capa.setTitle(req.title().trim());
        capa.setDescription(req.description());
        copyEditable(capa, req);
        capa = repo.save(capa);
        auditService.record("CAPA_UPDATE", "CapaRecord", String.valueOf(id), null, capa.getCapaNumber(), null);
        return toDto(capa);
    }

    @Transactional
    public CapaDto advance(Long id, AdvanceCapaRequest req) {
        CapaRecord capa = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("CAPA not found: " + id));
        if (req.target() == CapaStatus.CLOSED) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A CAPA is closed through the close action, which records the verification");
        }
        CapaStatus old = capa.getStatus();
        CapaStatus target = old.transitionTo(req.target());
        if (target == CapaStatus.ACTION_IN_PROGRESS && old == CapaStatus.ROOT_CAUSE_ANALYSIS
                && isBlank(capa.getRootCause())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Record the root cause before moving to action");
        }
        if (target == CapaStatus.VERIFICATION && isBlank(capa.getCorrectiveAction())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Record the corrective action before moving to verification");
        }
        if (old == CapaStatus.VERIFICATION && isBlank(req.reason())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A reason is required when verification sends a CAPA back to action");
        }
        capa.setStatus(target);
        capa = repo.save(capa);
        auditService.record("CAPA_ADVANCE", "CapaRecord", String.valueOf(id), old.name(), target.name(),
                req.reason());
        return toDto(capa);
    }

    @Transactional
    public CapaDto close(Long id, CloseCapaRequest req) {
        CapaRecord capa = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("CAPA not found: " + id));
        CapaStatus old = capa.getStatus();
        capa.setStatus(old.transitionTo(CapaStatus.CLOSED));
        capa.setVerifiedBy(QualitySecurity.requireUserId("close a CAPA"));
        capa.setVerificationNotes(req.verificationNotes().trim());
        capa.setClosedAt(Instant.now());
        capa = repo.save(capa);
        auditService.record("CAPA_CLOSE", "CapaRecord", String.valueOf(id), old.name(), CapaStatus.CLOSED.name(),
                req.verificationNotes());
        return toDto(capa);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private CapaRecord raise(String title, String description, CapaSourceType source, Long sourceId) {
        CapaRecord capa = new CapaRecord();
        capa.setCapaNumber(numberService.next("CAPA"));
        capa.setTitle(truncate(title, 200));
        capa.setDescription(description);
        capa.setSourceType(source);
        capa.setSourceId(sourceId);
        return capa;
    }

    private CapaRecord finishCreate(CapaRecord capa) {
        capa = repo.save(capa);
        auditService.record("CAPA_CREATE", "CapaRecord", String.valueOf(capa.getId()), null, capa.getCapaNumber(),
                capa.getSourceType() + (capa.getSourceId() == null ? "" : " #" + capa.getSourceId()));
        return capa;
    }

    private static void copyEditable(CapaRecord capa, SaveCapaRequest req) {
        capa.setRootCause(req.rootCause());
        capa.setCorrectiveAction(req.correctiveAction());
        capa.setPreventiveAction(req.preventiveAction());
        capa.setTargetCompletionDate(req.targetCompletionDate());
        capa.setAssignedTo(req.assignedTo());
    }

    private void validateSource(CapaSourceType type, Long sourceId) {
        if (sourceId == null) {
            if (type != CapaSourceType.INTERNAL_AUDIT) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "A " + type + " CAPA must reference its source record");
            }
            return;
        }
        switch (type) {
            case REJECTION_ANALYSIS -> {
                if (!rejectionRepo.existsById(sourceId)) {
                    throw new NotFoundException("Rejection not found: " + sourceId);
                }
            }
            case CUSTOMER_COMPLAINT -> throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Raise a CAPA for a complaint with the from-complaint action so the two stay linked");
            case SPC_VIOLATION -> spcService.requireOutOfControlPoint(sourceId);
            case INTERNAL_AUDIT -> {
                // an audit reference is free text elsewhere; nothing to check
            }
        }
        rejectOpenDuplicate(type, sourceId);
    }

    private void rejectOpenDuplicate(CapaSourceType type, Long sourceId) {
        if (repo.existsBySourceTypeAndSourceIdAndStatusNot(type, sourceId, CapaStatus.CLOSED)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "An open CAPA already exists for this " + type + " record");
        }
    }

    private CapaRecord load(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("CAPA not found: " + id));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private CapaDto toDto(CapaRecord c) {
        return new CapaDto(c.getId(), c.getCapaNumber(), c.getTitle(), c.getDescription(), c.getSourceType(),
                c.getSourceId(), sourceLabel(c), c.getRootCause(), c.getCorrectiveAction(), c.getPreventiveAction(),
                c.getVerificationNotes(), c.getTargetCompletionDate(), c.getStatus(), c.getStatus().next(),
                c.getAssignedTo(), c.getVerifiedBy(), c.getOpenedAt(), c.getClosedAt());
    }

    private String sourceLabel(CapaRecord c) {
        if (c.getSourceId() == null) {
            return null;
        }
        return switch (c.getSourceType()) {
            case CUSTOMER_COMPLAINT -> complaintRepo.findById(c.getSourceId())
                    .map(CustomerComplaint::getComplaintNumber).orElse(null);
            case REJECTION_ANALYSIS -> "Rejection #" + c.getSourceId();
            case SPC_VIOLATION -> "SPC point #" + c.getSourceId();
            case INTERNAL_AUDIT -> null;
        };
    }
}
