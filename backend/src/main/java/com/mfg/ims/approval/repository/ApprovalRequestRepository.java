package com.mfg.ims.approval.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.approval.domain.ApprovalRequest;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByDocumentTypeAndDocumentIdOrderByStepNoAsc(String docType, Long docId);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'PENDING' ORDER BY a.stepNo ASC")
    Optional<ApprovalRequest> findFirstPendingStep(@Param("docType") String docType, @Param("docId") Long docId);

    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'PENDING'")
    long countPendingSteps(@Param("docType") String docType, @Param("docId") Long docId);

    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'APPROVED'")
    long countApprovedSteps(@Param("docType") String docType, @Param("docId") Long docId);

    /** Pending requests whose required permission is held by the caller — shown in the approver inbox. */
    @Query("SELECT a FROM ApprovalRequest a WHERE a.status = 'PENDING' AND a.requiredPermission IN :permissions ORDER BY a.createdAt ASC")
    List<ApprovalRequest> findPendingByPermissions(@Param("permissions") Collection<String> permissions);

    /** Requests decided by a user — shown in their history. */
    @Query("SELECT a FROM ApprovalRequest a WHERE a.status IN ('APPROVED','REJECTED') AND a.decidedBy = :userId ORDER BY a.decidedAt DESC")
    List<ApprovalRequest> findDecidedBy(@Param("userId") Long userId);
}
