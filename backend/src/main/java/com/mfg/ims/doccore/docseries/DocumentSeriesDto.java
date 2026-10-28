package com.mfg.ims.doccore.docseries;

public record DocumentSeriesDto(
        Long id,
        String documentKind,
        String seriesCode,
        Long branchId,
        String format,
        String numbering,
        String resetPolicy,
        boolean isDefault,
        boolean active,
        boolean statutory) {

    public static DocumentSeriesDto from(DocumentSeries s) {
        return new DocumentSeriesDto(s.getId(), s.getDocumentKind(), s.getSeriesCode(),
                s.getBranchId(), s.getFormat(), s.getNumbering(), s.getResetPolicy(),
                s.isDefault(), s.isActive(), s.isStatutory());
    }
}
