package com.mfg.ims.analytics.reportbuilder;

import java.io.Serializable;
import java.util.Objects;

public class ReportViewCatalogueId implements Serializable {
    private String viewName;
    private String columnName;

    public ReportViewCatalogueId() {}

    public ReportViewCatalogueId(String viewName, String columnName) {
        this.viewName = viewName;
        this.columnName = columnName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportViewCatalogueId that)) return false;
        return Objects.equals(viewName, that.viewName) && Objects.equals(columnName, that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewName, columnName);
    }
}
