package com.mfg.ims.document.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_versions", schema = "ims")
public class DocumentVersion extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "revision_label", length = 20)
    private String revisionLabel;

    @Column(name = "storage_key", nullable = false, unique = true, length = 200)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long v) { this.documentId = v; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int v) { this.versionNo = v; }
    public String getRevisionLabel() { return revisionLabel; }
    public void setRevisionLabel(String v) { this.revisionLabel = v; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String v) { this.storageKey = v; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String v) { this.originalFilename = v; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String v) { this.mimeType = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public String getSha256() { return sha256; }
    public void setSha256(String v) { this.sha256 = v; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String v) { this.changeNote = v; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long v) { this.uploadedBy = v; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant v) { this.uploadedAt = v; }
}
