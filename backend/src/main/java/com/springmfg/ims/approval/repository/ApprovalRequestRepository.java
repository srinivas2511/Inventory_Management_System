package com.springmfg.ims.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.approval.domain.ApprovalRequest;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByDocumentTypeAndDocumentIdOrderByStepNoAsc(String docType, Long docId);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'PENDING' ORDER BY a.stepNo ASC")
    Optional<ApprovalRequest> findFirstPendingStep(@Param("docType") String docType, @Param("docId") Long docId);

    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'PENDING'")
    long countPendingSteps(@Param("docType") String docType, @Param("docId") Long docId);

    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.documentType = :docType AND a.documentId = :docId AND a.status = 'APPROVED'")
    long countApprovedSteps(@Param("docType") String docType, @Param("docId") Long docId);
}
