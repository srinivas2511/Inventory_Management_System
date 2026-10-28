package com.mfg.ims.doccore.comment;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class CommentService {

    private final CommentRepository repo;

    public CommentService(CommentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CommentDto> list(String entityType, Long entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByCreatedAt(entityType, entityId)
                .stream().map(CommentDto::from).toList();
    }

    @Transactional
    public CommentDto add(String entityType, Long entityId, Long parentId, String body, Long userId) {
        Comment c = new Comment();
        c.setEntityType(entityType);
        c.setEntityId(entityId);
        c.setParentId(parentId);
        c.setBody(body.trim());
        c.setCreatedBy(userId);
        return CommentDto.from(repo.save(c));
    }

    @Transactional
    public CommentDto edit(Long id, String body, Long userId) {
        Comment c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + id));
        if (!c.getCreatedBy().equals(userId)) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "Only the author can edit a comment");
        }
        c.setBody(body.trim());
        c.setEditedAt(Instant.now());
        c.setEditedBy(userId);
        return CommentDto.from(repo.save(c));
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        Comment c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + id));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SETTINGS_MANAGE") || a.getAuthority().equals("COMPANY_MANAGE") || a.getAuthority().equals("CUSTOM_FIELD_MANAGE"));
        if (!c.getCreatedBy().equals(userId) && !isAdmin) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "Cannot delete another user's comment");
        }
        c.setDeletedAt(Instant.now());
        repo.save(c);
    }
}
