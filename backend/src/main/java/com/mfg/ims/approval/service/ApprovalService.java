package com.mfg.ims.approval.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.approval.domain.ApprovalRequest;
import com.mfg.ims.approval.dto.ApprovalRequestDto;
import com.mfg.ims.approval.event.ApprovalCompletedEvent;
import com.mfg.ims.approval.repository.ApprovalRequestRepository;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.security.SegregationOfDuties;

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
        return steps.stream().map(step -> {
            ApprovalRequest ar = new ApprovalRequest();
            ar.setDocumentType(docType);
            ar.setDocumentId(docId);
            ar.setStepNo(step.stepNo());
            ar.setRequiredPermission(step.requiredPermission());
            ar.setRequestedBy(requestedBy);
            return repo.save(ar);
        }).toList();
    }

    /**
     * Approves the pending request identified by requestId.
     * Enforces: caller ≠ requester (SoD).
     * If all steps for the document are now approved → publishes ApprovalCompletedEvent.
     */
    @Transactional
    public ApprovalRequestDto approve(Long requestId, String comment) {
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

        long pending = repo.countPendingSteps(ar.getDocumentType(), ar.getDocumentId());
        if (pending == 0) {
            publisher.publishEvent(new ApprovalCompletedEvent(ar.getDocumentType(), ar.getDocumentId()));
        }
        return ApprovalRequestDto.from(ar);
    }

    /** Rejects the pending request. */
    @Transactional
    public ApprovalRequestDto reject(Long requestId, String comment) {
        ApprovalRequest ar = require(requestId);
        if (!ar.isPending()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Approval request is not PENDING");
        }
        ar.setStatus("REJECTED");
        ar.setDecidedBy(currentUserId());
        ar.setDecidedAt(Instant.now());
        ar.setComment(comment);
        return ApprovalRequestDto.from(repo.save(ar));
    }

    /**
     * Withdraws a pending request. Only the original requester may withdraw.
     */
    @Transactional
    public void withdraw(Long requestId) {
        ApprovalRequest ar = require(requestId);
        if (!ar.isPending()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Approval request " + requestId + " is not PENDING");
        }
        Long currentUserId = currentUserId();
        if (!currentUserId.equals(ar.getRequestedBy())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Only the requester can withdraw their own approval request");
        }
        ar.setStatus("WITHDRAWN");
        ar.setWithdrawnAt(Instant.now());
        repo.save(ar);
    }

    /** Returns pending requests the current user can decide (matched by their permissions). */
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> findPendingForCurrentUser() {
        Set<String> perms = currentUserPermissions();
        if (perms.isEmpty()) return List.of();
        return repo.findPendingByPermissions(perms).stream().map(ApprovalRequestDto::from).toList();
    }

    /** Returns requests the current user has already decided, newest first. */
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> findHistoryForCurrentUser() {
        return repo.findDecidedBy(currentUserId()).stream().map(ApprovalRequestDto::from).toList();
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
            if (auth != null && auth.getPrincipal() instanceof CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private Set<String> currentUserPermissions() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CachedUser user) {
                return user.permissions();
            }
        } catch (Exception ignored) {}
        return Set.of();
    }
}
