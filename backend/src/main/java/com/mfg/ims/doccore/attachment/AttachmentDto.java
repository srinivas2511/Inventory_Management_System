package com.mfg.ims.doccore.attachment;

import java.time.Instant;

public record AttachmentDto(
        Long id,
        String entityType,
        Long entityId,
        String fileName,
        String contentType,
        Long sizeBytes,
        Instant createdAt,
        Long createdBy) {

    public static AttachmentDto from(Attachment a) {
        return new AttachmentDto(a.getId(), a.getEntityType(), a.getEntityId(),
                a.getFileName(), a.getContentType(), a.getSizeBytes(),
                a.getCreatedAt(), a.getCreatedBy());
    }
}
