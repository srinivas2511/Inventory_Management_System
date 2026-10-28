package com.mfg.ims.doccore.comment;

import java.time.Instant;

public record CommentDto(
        Long id,
        String entityType,
        Long entityId,
        Long parentId,
        String body,
        Instant createdAt,
        Long createdBy,
        Instant editedAt,
        boolean deleted) {

    public static CommentDto from(Comment c) {
        String body = c.getDeletedAt() != null ? "[deleted]" : c.getBody();
        return new CommentDto(c.getId(), c.getEntityType(), c.getEntityId(),
                c.getParentId(), body, c.getCreatedAt(), c.getCreatedBy(),
                c.getEditedAt(), c.getDeletedAt() != null);
    }
}
