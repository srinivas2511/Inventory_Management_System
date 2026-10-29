package com.mfg.ims.analytics.reportbuilder;

import jakarta.persistence.*;

@Entity
@Table(name = "report_view_catalogue", schema = "ims")
@IdClass(ReportViewCatalogueId.class)
public class ReportViewCatalogueEntry {

    @Id
    @Column(name = "view_name", length = 50)
    private String viewName;

    @Id
    @Column(name = "column_name", length = 60)
    private String columnName;

    @Column(name = "data_type", length = 20)
    private String dataType;

    @Column(name = "label", length = 80)
    private String label;

    @Column(name = "aggregations", length = 40)
    private String aggregations;

    @Column(name = "permission_code", length = 60)
    private String permissionCode;

    public String getViewName() { return viewName; }
    public String getColumnName() { return columnName; }
    public String getDataType() { return dataType; }
    public String getLabel() { return label; }
    public String getAggregations() { return aggregations; }
    public String getPermissionCode() { return permissionCode; }
}
