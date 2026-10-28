package com.mfg.ims.procurement.purchasereturn;

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
@Table(name = "purchase_returns", schema = "ims")
public class PurchaseReturn extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "grn_id")
    private Long grnId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "reason_code", length = 20)
    private String reasonCode;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "debit_note_id")
    private Long debitNoteId;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseReturnItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }
    public Long getGrnId() { return grnId; }
    public void setGrnId(Long v) { this.grnId = v; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long v) { this.invoiceId = v; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String v) { this.reasonCode = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public Long getDebitNoteId() { return debitNoteId; }
    public void setDebitNoteId(Long v) { this.debitNoteId = v; }
    public List<PurchaseReturnItem> getItems() { return items; }
}
