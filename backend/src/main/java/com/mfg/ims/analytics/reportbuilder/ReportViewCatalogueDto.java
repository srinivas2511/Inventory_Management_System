package com.mfg.ims.analytics.reportbuilder;

public record ReportViewCatalogueDto(
        String viewName,
        String columnName,
        String dataType,
        String label,
        String aggregations,
        String permissionCode) {}
