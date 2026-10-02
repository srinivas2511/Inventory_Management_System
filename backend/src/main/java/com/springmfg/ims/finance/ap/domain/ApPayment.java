package com.springmfg.ims.finance.ap.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ap_payments", schema = "ims")
public class ApPayment extends BaseEntity {

    @Column(name = "payment_reference", nullable = false, unique = true, length = 25)
    private String paymentNumber;

    @Column(name = "ap_invoice_id", nullable = false)
    private Long apInvoiceId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMode = "BANK_TRANSFER";

    @Column(name = "bank_reference", length = 60)
    private String referenceNumber;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    public String getPaymentNumber() { return paymentNumber; }
    public Long getApInvoiceId() { return apInvoiceId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMode() { return paymentMode; }
    public String getReferenceNumber() { return referenceNumber; }
    public Long getJournalHeaderId() { return journalHeaderId; }

    public void setPaymentNumber(String paymentNumber) { this.paymentNumber = paymentNumber; }
    public void setApInvoiceId(Long apInvoiceId) { this.apInvoiceId = apInvoiceId; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public void setJournalHeaderId(Long journalHeaderId) { this.journalHeaderId = journalHeaderId; }
}
