package com.mfg.ims.integration.importer;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    /** {@code status} is OK (valid, dry run), CREATED, or ERROR. */
    public record RowResult(int rowNumber, String key, String status, String message) {
    }

    public record ImportResult(String entity, boolean dryRun, int totalRows, int valid, int created, int failed,
                               List<RowResult> rows, boolean rowsTruncated) {
    }
}
