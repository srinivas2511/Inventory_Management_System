package com.springmfg.ims.document.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.document.domain.Document;
import com.springmfg.ims.document.domain.DocumentCategory;
import com.springmfg.ims.document.domain.DocumentLink;
import com.springmfg.ims.document.domain.DocumentStatus;
import com.springmfg.ims.document.domain.DocumentVersion;
import com.springmfg.ims.document.dto.DocumentDtos.AddVersionRequest;
import com.springmfg.ims.document.dto.DocumentDtos.CategoryDto;
import com.springmfg.ims.document.dto.DocumentDtos.CreateDocumentRequest;
import com.springmfg.ims.document.dto.DocumentDtos.DocumentDto;
import com.springmfg.ims.document.dto.DocumentDtos.DocumentSummaryDto;
import com.springmfg.ims.document.dto.DocumentDtos.LinkDto;
import com.springmfg.ims.document.dto.DocumentDtos.LinkRequest;
import com.springmfg.ims.document.dto.DocumentDtos.SaveCategoryRequest;
import com.springmfg.ims.document.dto.DocumentDtos.VersionDto;
import com.springmfg.ims.document.repository.DocumentCategoryRepository;
import com.springmfg.ims.document.repository.DocumentLinkRepository;
import com.springmfg.ims.document.repository.DocumentRepository;
import com.springmfg.ims.document.repository.DocumentVersionRepository;
import com.springmfg.ims.document.storage.DocumentStorage;
import com.springmfg.ims.document.storage.DocumentStorage.ContentTooLargeException;
import com.springmfg.ims.document.storage.DocumentStorage.StoredObject;

/**
 * Documents with versions and links to records. Content lives in {@link DocumentStorage}; this service owns the
 * rules: categories that demand extra authority, per-link read access, the state machine, segregation of duties on
 * approval and an audit entry for every upload, change of state and download.
 */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentLinkRepository links;
    private final DocumentCategoryRepository categories;
    private final DocumentStorage storage;
    private final UploadPolicy policy;
    private final DocumentAccess access;
    private final DocumentLinkTargets targets;
    private final DocumentNumberService numbers;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                           DocumentLinkRepository links, DocumentCategoryRepository categories,
                           DocumentStorage storage, UploadPolicy policy, DocumentAccess access,
                           DocumentLinkTargets targets, DocumentNumberService numbers, AuditService audit) {
        this.documents = documents;
        this.versions = versions;
        this.links = links;
        this.categories = categories;
        this.storage = storage;
        this.policy = policy;
        this.access = access;
        this.targets = targets;
        this.numbers = numbers;
        this.audit = audit;
    }

    /** A downloaded file: the stream belongs to the caller, who must close it. */
    public record DocumentDownload(String filename, String mimeType, long sizeBytes, InputStream content) {
    }

    // ---- categories -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categories.findAllByOrderByNameAsc().stream().map(DocumentService::toDto).toList();
    }

    @Transactional
    public CategoryDto saveCategory(Long id, SaveCategoryRequest req) {
        DocumentCategory c = id == null ? new DocumentCategory()
                : categories.findById(id).orElseThrow(() -> new NotFoundException("Document category", id));
        String code = req.categoryCode().trim().toUpperCase();
        Long ownId = c.getId();
        categories.findByCategoryCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(ownId)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Category code already exists: " + code);
            }
        });
        c.setCategoryCode(code);
        c.setName(req.name().trim());
        c.setRetentionYears(req.retentionYears());
        c.setRequiredAuthority(req.requiredAuthority() == null || req.requiredAuthority().isBlank()
                ? null : req.requiredAuthority().trim());
        if (req.active() != null) {
            c.setActive(req.active());
        }
        c = categories.save(c);
        audit.record(id == null ? "DOCUMENT_CATEGORY_CREATED" : "DOCUMENT_CATEGORY_UPDATED", "DocumentCategory",
                String.valueOf(c.getId()), null, c.getCategoryCode(), null);
        return toDto(c);
    }

    // ---- queries --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<DocumentSummaryDto> search(String q, String categoryCode, DocumentStatus status, String entityType,
                                           Long entityId, Pageable pageable) {
        Long categoryId = categoryCode == null || categoryCode.isBlank() ? null
                : categories.findByCategoryCode(categoryCode.trim().toUpperCase()).map(DocumentCategory::getId)
                        .orElse(-1L);
        Page<Document> page = documents.search(q, categoryId, status == null ? null : List.of(status), entityType,
                entityId, access.seesEverything(), access.readableTypes(), pageable);
        Map<Long, DocumentCategory> cats = categoryMap();
        Map<Long, List<DocumentLink>> linkMap = links.findByDocumentIdIn(page.map(Document::getId).getContent())
                .stream().collect(Collectors.groupingBy(DocumentLink::getDocumentId));
        return page.map(d -> {
            DocumentCategory c = cats.get(d.getCategoryId());
            return new DocumentSummaryDto(d.getId(), d.getDocumentNumber(), d.getTitle(),
                    c == null ? null : c.getCategoryCode(), c == null ? null : c.getName(), d.getStatus(),
                    d.getCurrentVersionNo(), d.getUpdatedAt(),
                    linkMap.getOrDefault(d.getId(), Collections.emptyList()).stream().map(DocumentService::toDto)
                            .toList());
        });
    }

    @Transactional(readOnly = true)
    public DocumentDto get(Long id) {
        Document d = require(id);
        List<DocumentLink> ls = links.findByDocumentId(id);
        access.requireRead(ls);
        return toDto(d, ls);
    }

    // ---- upload ---------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDto create(CreateDocumentRequest req, MultipartFile file) {
        DocumentCategory category = categories.findByCategoryCode(req.categoryCode().trim().toUpperCase())
                .filter(DocumentCategory::isActive)
                .orElseThrow(() -> new NotFoundException("Document category", req.categoryCode()));
        access.requireCategoryAuthority(category);
        List<LinkRequest> wanted = req.links() == null ? List.of() : req.links();
        wanted.forEach(this::validateLink);

        Document d = new Document();
        d.setDocumentNumber(numbers.next("DOC"));
        d.setCategoryId(category.getId());
        d.setTitle(req.title().trim());
        d.setDescription(req.description());
        d = documents.save(d);

        DocumentVersion v = storeVersion(d, 1, req.revisionLabel(), req.changeNote(), file);
        for (LinkRequest l : wanted) {
            addLinkRow(d.getId(), l);
        }
        audit.record("DOCUMENT_UPLOADED", "Document", String.valueOf(d.getId()), null,
                d.getDocumentNumber() + " v1", category.getCategoryCode() + ": " + v.getOriginalFilename());
        return toDto(d, links.findByDocumentId(d.getId()));
    }

    @Transactional
    public DocumentDto addVersion(Long id, AddVersionRequest req, MultipartFile file) {
        Document d = documents.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Document", id));
        List<DocumentLink> ls = links.findByDocumentId(id);
        access.requireRead(ls);
        access.requireCategoryAuthority(category(d));
        if (d.getStatus() == DocumentStatus.OBSOLETE) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "An obsolete document cannot receive a new version");
        }
        int next = d.getCurrentVersionNo() + 1;
        DocumentVersion v = storeVersion(d, next, req == null ? null : req.revisionLabel(),
                req == null ? null : req.changeNote(), file);
        d.setCurrentVersionNo(next);
        if (d.getStatus() == DocumentStatus.REVIEW || d.getStatus() == DocumentStatus.APPROVED) {
            audit.record("DOCUMENT_RETURNED_TO_DRAFT", "Document", String.valueOf(id), d.getStatus().name(),
                    DocumentStatus.DRAFT.name(), "New version uploaded");
            d.setStatus(DocumentStatus.DRAFT);
            d.setStatusReason("New version " + next + " uploaded; it needs approval again");
            d.setApprovedBy(null);
            d.setApprovedAt(null);
        }
        d = documents.save(d);
        audit.record("DOCUMENT_VERSION_ADDED", "Document", String.valueOf(id), "v" + (next - 1),
                d.getDocumentNumber() + " v" + next, v.getOriginalFilename());
        return toDto(d, ls);
    }

    // ---- links ----------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDto addLink(Long id, LinkRequest req) {
        Document d = require(id);
        List<DocumentLink> existing = links.findByDocumentId(id);
        access.requireRead(existing);
        access.requireCategoryAuthority(category(d));
        validateLink(req);
        if (links.findByDocumentIdAndEntityTypeAndEntityId(id, req.entityType(), req.entityId()).isPresent()) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "The document is already linked to that record");
        }
        addLinkRow(id, req);
        audit.record("DOCUMENT_LINKED", "Document", String.valueOf(id), null, req.entityType() + "#" + req.entityId(),
                null);
        return toDto(d, links.findByDocumentId(id));
    }

    @Transactional
    public DocumentDto removeLink(Long id, Long linkId) {
        Document d = require(id);
        List<DocumentLink> existing = links.findByDocumentId(id);
        access.requireRead(existing);
        access.requireCategoryAuthority(category(d));
        DocumentLink link = links.findByIdAndDocumentId(linkId, id)
                .orElseThrow(() -> new NotFoundException("Document link", linkId));
        links.delete(link);
        audit.record("DOCUMENT_UNLINKED", "Document", String.valueOf(id), link.getEntityType() + "#"
                + link.getEntityId(), null, null);
        return toDto(d, links.findByDocumentId(id));
    }

    // ---- state machine --------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDto submit(Long id) {
        return transition(id, DocumentStatus.REVIEW, "DOCUMENT_SUBMITTED", null);
    }

    @Transactional
    public DocumentDto approve(Long id) {
        Document d = documents.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Document", id));
        DocumentVersion current = versions.findByDocumentIdAndVersionNo(id, d.getCurrentVersionNo())
                .orElseThrow(() -> new NotFoundException("Document version", d.getCurrentVersionNo()));
        if (currentUserId().equals(current.getUploadedBy())) {
            throw new BusinessRuleException(ErrorCode.SEGREGATION_OF_DUTIES,
                    "The person who uploaded the current version cannot approve it");
        }
        return transition(d, DocumentStatus.APPROVED, "DOCUMENT_APPROVED", null);
    }

    @Transactional
    public DocumentDto reject(Long id, String reason) {
        return transition(id, DocumentStatus.DRAFT, "DOCUMENT_REJECTED", reason);
    }

    @Transactional
    public DocumentDto obsolete(Long id, String reason) {
        return transition(id, DocumentStatus.OBSOLETE, "DOCUMENT_OBSOLETED", reason);
    }

    private DocumentDto transition(Long id, DocumentStatus target, String action, String reason) {
        Document d = documents.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Document", id));
        return transition(d, target, action, reason);
    }

    private DocumentDto transition(Document d, DocumentStatus target, String action, String reason) {
        List<DocumentLink> ls = links.findByDocumentId(d.getId());
        access.requireRead(ls);
        DocumentStatus from = d.getStatus();
        if (!from.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A document cannot move from " + from + " to " + target);
        }
        d.setStatus(target);
        d.setStatusReason(reason);
        if (target == DocumentStatus.APPROVED) {
            d.setApprovedBy(currentUserId());
            d.setApprovedAt(Instant.now());
        } else if (target == DocumentStatus.DRAFT) {
            d.setApprovedBy(null);
            d.setApprovedAt(null);
        }
        d = documents.save(d);
        audit.record(action, "Document", String.valueOf(d.getId()), from.name(), target.name(), reason);
        return toDto(d, ls);
    }

    // ---- download -------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDownload download(Long id, Integer versionNo) {
        Document d = require(id);
        access.requireRead(links.findByDocumentId(id));
        int number = versionNo == null ? d.getCurrentVersionNo() : versionNo;
        DocumentVersion v = versions.findByDocumentIdAndVersionNo(id, number)
                .orElseThrow(() -> new NotFoundException("Document version", number));
        InputStream content;
        try {
            content = storage.open(v.getStorageKey());
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Stored content of " + d.getDocumentNumber() + " v" + number);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored document content", e);
        }
        audit.record("DOCUMENT_DOWNLOADED", "Document", String.valueOf(id), null,
                d.getDocumentNumber() + " v" + number, v.getOriginalFilename());
        return new DocumentDownload(UploadPolicy.sanitizeFilename(v.getOriginalFilename()), v.getMimeType(),
                v.getSizeBytes(), content);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private DocumentVersion storeVersion(Document d, int versionNo, String revisionLabel, String changeNote,
                                         MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A file is required");
        }
        String filename = UploadPolicy.sanitizeFilename(file.getOriginalFilename());
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(16);
            byte[] head = in.readNBytes(16);
            in.reset();
            String mime = policy.validate(file.getOriginalFilename(), head);
            StoredObject stored = storage.store(in, policy.maxBytes());
            deleteOnRollback(stored.storageKey());

            DocumentVersion v = new DocumentVersion();
            v.setDocumentId(d.getId());
            v.setVersionNo(versionNo);
            v.setRevisionLabel(blankToNull(revisionLabel));
            v.setStorageKey(stored.storageKey());
            v.setOriginalFilename(filename);
            v.setMimeType(mime);
            v.setSizeBytes(stored.sizeBytes());
            v.setSha256(stored.sha256());
            v.setChangeNote(blankToNull(changeNote));
            v.setUploadedBy(currentUserId());
            return versions.save(v);
        } catch (ContentTooLargeException e) {
            throw new BusinessRuleException(ErrorCode.FILE_TOO_LARGE,
                    "The file is larger than " + policy.maxBytes() / (1024 * 1024) + " MB");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded document", e);
        }
    }

    private void deleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(storageKey);
                }
            }
        });
    }

    private void validateLink(LinkRequest req) {
        String type = req.entityType() == null ? "" : req.entityType().trim().toUpperCase();
        if (!targets.types().contains(type)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Documents cannot be linked to " + type);
        }
        if (!targets.exists(type, req.entityId())) {
            throw new NotFoundException(type, req.entityId());
        }
        access.requireCanLink(type);
    }

    private void addLinkRow(Long documentId, LinkRequest req) {
        DocumentLink l = new DocumentLink();
        l.setDocumentId(documentId);
        l.setEntityType(req.entityType().trim().toUpperCase());
        l.setEntityId(req.entityId());
        links.save(l);
    }

    private Document require(Long id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("Document", id));
    }

    private DocumentCategory category(Document d) {
        return categories.findById(d.getCategoryId()).orElseThrow(() -> new NotFoundException("Document category",
                d.getCategoryId()));
    }

    private Map<Long, DocumentCategory> categoryMap() {
        return categories.findAll().stream().collect(Collectors.toMap(DocumentCategory::getId, Function.identity()));
    }

    private DocumentDto toDto(Document d, Collection<DocumentLink> ls) {
        DocumentCategory c = category(d);
        List<VersionDto> vs = versions.findByDocumentIdOrderByVersionNoDesc(d.getId()).stream()
                .map(v -> new VersionDto(v.getId(), v.getVersionNo(), v.getRevisionLabel(), v.getOriginalFilename(),
                        v.getMimeType(), v.getSizeBytes(), v.getSha256(), v.getChangeNote(), v.getUploadedBy(),
                        v.getUploadedAt()))
                .toList();
        return new DocumentDto(d.getId(), d.getDocumentNumber(), d.getTitle(), d.getDescription(),
                c.getCategoryCode(), c.getName(), d.getStatus(), d.getStatusReason(), d.getCurrentVersionNo(),
                d.getApprovedBy(), d.getApprovedAt(), d.getCreatedAt(), d.getUpdatedAt(), vs,
                ls.stream().map(DocumentService::toDto).toList());
    }

    private static LinkDto toDto(DocumentLink l) {
        return new LinkDto(l.getId(), l.getEntityType(), l.getEntityId());
    }

    private static CategoryDto toDto(DocumentCategory c) {
        return new CategoryDto(c.getId(), c.getCategoryCode(), c.getName(), c.getRetentionYears(),
                c.getRequiredAuthority(), c.isActive());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        throw new IllegalStateException("No authenticated user");
    }
}
