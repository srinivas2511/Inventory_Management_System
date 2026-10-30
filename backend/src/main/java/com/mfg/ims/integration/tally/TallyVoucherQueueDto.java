package com.mfg.ims.integration.tally;

import java.time.OffsetDateTime;

public record TallyVoucherQueueDto(
        Long id,
        String sourceType,
        Long sourceId,
        String voucherType,
        String status,
        String exceptionCode,
        String exceptionDetail,
        Long approvedBy,
        OffsetDateTime approvedAt,
        OffsetDateTime sentAt,
        OffsetDateTime reconciledAt,
        OffsetDateTime createdAt) {

    static TallyVoucherQueueDto from(TallyVoucherQueue q) {
        return new TallyVoucherQueueDto(q.getId(), q.getSourceType(), q.getSourceId(),
                q.getVoucherType(), q.getStatus(), q.getExceptionCode(), q.getExceptionDetail(),
                q.getApprovedBy(), q.getApprovedAt(), q.getSentAt(), q.getReconciledAt(), q.getCreatedAt());
    }
}
