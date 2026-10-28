package com.mfg.ims.doccore.importjob;

import java.time.Instant;

public record ImportJobDto(
        Long id,
        String entityType,
        String fileName,
        String status,
        Integer totalRows,
        int importedRows,
        int errorRows,
        int processedRows,
        int failedRows,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {

    public static ImportJobDto from(ImportJob j) {
        return new ImportJobDto(j.getId(), j.getEntityType(), j.getFileName(), j.getStatus(),
                j.getTotalRows(), j.getImportedRows(), j.getErrorRows(),
                j.getImportedRows(), j.getErrorRows(),
                j.getStartedAt(), j.getCompletedAt(), j.getCreatedAt());
    }
}
