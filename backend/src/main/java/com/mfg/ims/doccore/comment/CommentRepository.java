package com.mfg.ims.doccore.comment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByEntityTypeAndEntityIdOrderByCreatedAt(String entityType, Long entityId);
}
