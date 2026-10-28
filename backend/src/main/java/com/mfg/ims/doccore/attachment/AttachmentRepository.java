package com.mfg.ims.doccore.attachment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
