package com.mfg.ims.procurement.rfq;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "rfqs", schema = "ims")
public class Rfq extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "requisition_id")
    private Long requisitionId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "delivery_location", length = 200)
    private String deliveryLocation;

    @Column(name = "terms_id")
    private Long termsId;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<RfqItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getRequisitionId() { return requisitionId; }
    public void setRequisitionId(Long v) { this.requisitionId = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getDeliveryLocation() { return deliveryLocation; }
    public void setDeliveryLocation(String v) { this.deliveryLocation = v; }
    public Long getTermsId() { return termsId; }
    public void setTermsId(Long v) { this.termsId = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public List<RfqItem> getItems() { return items; }
}
