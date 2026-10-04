package com.springmfg.ims.document.dto;

import java.time.Instant;
import java.util.List;

import com.springmfg.ims.document.domain.DocumentStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record CategoryDto(Long id, String categoryCode, String name, Integer retentionYears,
                              String requiredAuthority, boolean active) {
    }

    public record SaveCategoryRequest(@NotBlank @Size(max = 20) String categoryCode,
                                      @NotBlank @Size(max = 80) String name,
                                      Integer retentionYears,
                                      @Size(max = 40) String requiredAuthority,
                                      Boolean active) {
    }

    public record LinkDto(Long id, String entityType, Long entityId) {
    }

    public record LinkRequest(@NotBlank @Size(max = 20) String entityType, @NotNull Long entityId) {
    }

    public record VersionDto(Long id, int versionNo, String revisionLabel, String originalFilename, String mimeType,
                             long sizeBytes, String sha256, String changeNote, Long uploadedBy, Instant uploadedAt) {
    }

    public record DocumentSummaryDto(Long id, String documentNumber, String title, String categoryCode,
                                     String categoryName, DocumentStatus status, int currentVersionNo,
                                     Instant updatedAt, List<LinkDto> links) {
    }

    public record DocumentDto(Long id, String documentNumber, String title, String description, String categoryCode,
                              String categoryName, DocumentStatus status, String statusReason, int currentVersionNo,
                              Long approvedBy, Instant approvedAt, Instant createdAt, Instant updatedAt,
                              List<VersionDto> versions, List<LinkDto> links) {
    }

    public record CreateDocumentRequest(@NotBlank @Size(max = 20) String categoryCode,
                                        @NotBlank @Size(max = 200) String title,
                                        @Size(max = 500) String description,
                                        @Size(max = 20) String revisionLabel,
                                        @Size(max = 500) String changeNote,
                                        @Valid List<LinkRequest> links) {
    }

    public record AddVersionRequest(@Size(max = 20) String revisionLabel, @Size(max = 500) String changeNote) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
