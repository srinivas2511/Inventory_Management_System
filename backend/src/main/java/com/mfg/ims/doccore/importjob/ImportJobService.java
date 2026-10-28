package com.mfg.ims.doccore.importjob;

import java.io.IOException;
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
import com.mfg.ims.doccore.attachment.AttachmentStorageService;

@Service
public class ImportJobService {

    private final ImportJobRepository repo;
    private final AttachmentStorageService storage;

    public ImportJobService(ImportJobRepository repo, AttachmentStorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ImportJobDto> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(1L).stream().map(ImportJobDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ImportJobDto getById(Long id) {
        return repo.findById(id).map(ImportJobDto::from)
                .orElseThrow(() -> new NotFoundException("Import job not found: " + id));
    }

    @Transactional
    public ImportJobDto create(String entityType, MultipartFile file) throws IOException {
        String key = storage.store(1L, "import", 0L, file);
        ImportJob job = new ImportJob();
        job.setEntityType(entityType.toUpperCase().trim());
        job.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.csv");
        job.setStorageKey(key);
        job.setCreatedBy(currentUserId());
        return ImportJobDto.from(repo.save(job));
    }

    @Transactional
    public ImportJobDto cancel(Long id) {
        ImportJob job = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import job not found: " + id));
        if (!"QUEUED".equals(job.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only QUEUED jobs can be cancelled");
        }
        job.setStatus("CANCELLED");
        return ImportJobDto.from(repo.save(job));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return 1L;
    }
}
