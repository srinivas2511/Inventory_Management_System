package com.mfg.ims.doccore.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class AttachmentService {

    private final AttachmentRepository repo;
    private final AttachmentStorageService storage;

    private static final long MAX_SIZE = 20L * 1024 * 1024; // 20 MB

    public AttachmentService(AttachmentRepository repo, AttachmentStorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(String entityType, Long entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream().map(AttachmentDto::from).toList();
    }

    @Transactional
    public AttachmentDto upload(String entityType, Long entityId, MultipartFile file, Long userId) throws IOException {
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessRuleException(ErrorCode.FILE_TOO_LARGE, "Attachment exceeds 20 MB limit");
        }
        String key = storage.store(1L, entityType, entityId, file);
        Attachment a = new Attachment();
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStorageKey(key);
        a.setCreatedBy(userId);
        return AttachmentDto.from(repo.save(a));
    }

    public InputStream openForDownload(Long id) throws IOException {
        Attachment a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));
        return storage.open(a.getStorageKey());
    }

    public String getFileName(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id))
                .getFileName();
    }

    @Transactional
    public void delete(Long id, Long userId) throws IOException {
        Attachment a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("SETTINGS_MANAGE") || ga.getAuthority().equals("COMPANY_MANAGE"));
        if (!a.getCreatedBy().equals(userId) && !isAdmin) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "Cannot delete another user's attachment");
        }
        storage.delete(a.getStorageKey());
        repo.deleteById(id);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
