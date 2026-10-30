package com.mfg.ims.approval.dto;

import java.time.Instant;

import com.mfg.ims.approval.domain.ApprovalRequest;

public record ApprovalRequestDto(
        Long id,
        String documentType,
        Long documentId,
        Integer stepNo,
        Integer stepGroup,
        String requiredPermission,
        String status,
        Long requestedBy,
        Long decidedBy,
        Instant decidedAt,
        String comment,
        Instant createdAt) {

    public static ApprovalRequestDto from(ApprovalRequest ar) {
        return new ApprovalRequestDto(ar.getId(), ar.getDocumentType(), ar.getDocumentId(),
                ar.getStepNo(), ar.getStepGroup(), ar.getRequiredPermission(), ar.getStatus(),
                ar.getRequestedBy(), ar.getDecidedBy(), ar.getDecidedAt(), ar.getComment(), ar.getCreatedAt());
    }
}
