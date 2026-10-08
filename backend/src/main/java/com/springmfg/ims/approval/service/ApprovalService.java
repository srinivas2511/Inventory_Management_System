package com.springmfg.ims.approval.service;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.approval.domain.ApprovalRequest;
import com.springmfg.ims.approval.event.ApprovalCompletedEvent;
import com.springmfg.ims.approval.repository.ApprovalRequestRepository;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.security.SegregationOfDuties;

/**
 * Generic multi-step approval engine (DESIGN.md §4.5).
 * Document owners request approval; holders of the required permission approve/reject.
 * Segregation of duties: requester cannot approve own request.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repo;
    private final ApplicationEventPublisher publisher;

    public ApprovalService(ApprovalRequestRepository repo, ApplicationEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    public record StepDefinition(int stepNo, String requiredPermission) {}

    /**
     * Creates ordered approval_request rows for a document.
     * Steps are created as PENDING; the first step is immediately actionable.
     */
    @Transactional
    public List<ApprovalRequest> requestApproval(String docType, Long docId,
                                                  Long requestedBy, List<StepDefinition> steps) {
        List<ApprovalRequest> created = steps.stream().map(step -> {
            ApprovalRequest ar = new ApprovalRequest();
            ar.setDocumentType(docType);
            ar.setDocumentId(docId);
            ar.setStepNo(step.stepNo());
            ar.setRequiredPermission(step.requiredPermission());
            ar.setRequestedBy(requestedBy);
            return repo.save(ar);
        }).toList();
        return created;
    }

    /**
     * Approves the current pending step.
     * Enforces: caller holds required_permission; caller ≠ requester.
     * If all steps approved → publishes ApprovalCompletedEvent.
     */
    @Transactional
    public ApprovalRequest approve(Long requestId, String comment) {
        ApprovalRequest ar = require(requestId);
        if (!ar.isPending()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Approval request " + requestId + " is not PENDING");
        }

        Long currentUserId = currentUserId();
        SegregationOfDuties.requireDifferentUser(currentUserId, ar.getRequestedBy(),
                "Requester cannot approve their own request");

        ar.setStatus("APPROVED");
        ar.setDecidedBy(currentUserId);
        ar.setDecidedAt(Instant.now());
        ar.setComment(comment);
        repo.save(ar);

        // Check if all steps for this document are now approved
        long pending = repo.countPendingSteps(ar.getDocumentType(), ar.getDocumentId());
        if (pending == 0) {
            publisher.publishEvent(new ApprovalCompletedEvent(ar.getDocumentType(), ar.getDocumentId()));
        }
        return ar;
    }

    /**
     * Rejects the current pending step.
     */
    @Transactional
    public ApprovalRequest reject(Long requestId, String comment) {
        ApprovalRequest ar = require(requestId);
        if (!ar.isPending()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Approval request is not PENDING");
        }
        ar.setStatus("REJECTED");
        ar.setDecidedBy(currentUserId());
        ar.setDecidedAt(Instant.now());
        ar.setComment(comment);
        return repo.save(ar);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> findByDocument(String docType, Long docId) {
        return repo.findByDocumentTypeAndDocumentIdOrderByStepNoAsc(docType, docId);
    }

    @Transactional(readOnly = true)
    public ApprovalRequest getById(Long id) {
        return require(id);
    }

    private ApprovalRequest require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Approval request not found: " + id));
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
