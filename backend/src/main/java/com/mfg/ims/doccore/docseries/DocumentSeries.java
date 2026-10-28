package com.mfg.ims.doccore.docseries;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_series", schema = "ims")
public class DocumentSeries extends BaseEntity {

    @Column(name = "document_kind", nullable = false, length = 30)
    private String documentKind;

    @Column(name = "series_code", nullable = false, length = 20)
    private String seriesCode;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "format", nullable = false, length = 80)
    private String format;

    @Column(name = "numbering", nullable = false, length = 10)
    private String numbering = "AUTO";

    @Column(name = "reset_policy", nullable = false, length = 10)
    private String resetPolicy = "FY";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "statutory", nullable = false)
    private boolean statutory = false;

    public DocumentSeries() {}

    public String getDocumentKind() { return documentKind; }
    public void setDocumentKind(String v) { this.documentKind = v; }
    public String getSeriesCode() { return seriesCode; }
    public void setSeriesCode(String v) { this.seriesCode = v; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long v) { this.branchId = v; }
    public String getFormat() { return format; }
    public void setFormat(String v) { this.format = v; }
    public String getNumbering() { return numbering; }
    public void setNumbering(String v) { this.numbering = v; }
    public String getResetPolicy() { return resetPolicy; }
    public void setResetPolicy(String v) { this.resetPolicy = v; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean v) { this.isDefault = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isStatutory() { return statutory; }
    public void setStatutory(boolean v) { this.statutory = v; }
}
