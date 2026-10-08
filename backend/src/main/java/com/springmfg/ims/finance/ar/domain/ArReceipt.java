package com.springmfg.ims.finance.ar.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ar_receipts", schema = "ims")
public class ArReceipt extends BaseEntity {

    @Column(name = "receipt_reference", nullable = false, unique = true, length = 25)
    private String receiptNumber;

    @Column(name = "ar_invoice_id", nullable = false)
    private Long arInvoiceId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMode = "BANK_TRANSFER";

    @Column(name = "bank_reference", length = 60)
    private String referenceNumber;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "carrying_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal carryingInr = BigDecimal.ZERO;

    @Column(name = "fx_difference", nullable = false, precision = 18, scale = 4)
    private BigDecimal fxDifference = BigDecimal.ZERO;

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getCarryingInr() { return carryingInr; }
    public BigDecimal getFxDifference() { return fxDifference; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public void setCarryingInr(BigDecimal carryingInr) { this.carryingInr = carryingInr; }
    public void setFxDifference(BigDecimal fxDifference) { this.fxDifference = fxDifference; }

    public String getReceiptNumber() { return receiptNumber; }
    public Long getArInvoiceId() { return arInvoiceId; }
    public LocalDate getReceiptDate() { return receiptDate; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMode() { return paymentMode; }
    public String getReferenceNumber() { return referenceNumber; }
    public Long getJournalHeaderId() { return journalHeaderId; }

    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public void setArInvoiceId(Long arInvoiceId) { this.arInvoiceId = arInvoiceId; }
    public void setReceiptDate(LocalDate receiptDate) { this.receiptDate = receiptDate; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public void setJournalHeaderId(Long journalHeaderId) { this.journalHeaderId = journalHeaderId; }
}
