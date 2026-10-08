package com.springmfg.ims.finance.ar.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ar_invoices", schema = "ims")
public class ArInvoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, unique = true, length = 25)
    private String invoiceNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "received_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    private String status = "OPEN";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "credited_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    public BigDecimal getCreditedAmount() { return creditedAmount; }
    public void setCreditedAmount(BigDecimal creditedAmount) { this.creditedAmount = creditedAmount; }

    @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "total_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalInr = BigDecimal.ZERO;

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public BigDecimal getTotalInr() { return totalInr; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public void setTotalInr(BigDecimal totalInr) { this.totalInr = totalInr; }

    @Column(name = "supply_type", length = 5)
    private String supplyType;

    public String getSupplyType() { return supplyType; }
    public void setSupplyType(String supplyType) { this.supplyType = supplyType; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public Long getCustomerId() { return customerId; }
    public Long getDispatchId() { return dispatchId; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public Long getPeriodId() { return periodId; }
    public Long getJournalHeaderId() { return journalHeaderId; }

    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setDispatchId(Long dispatchId) { this.dispatchId = dispatchId; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxTotal = taxTotal; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setReceivedAmount(BigDecimal receivedAmount) { this.receivedAmount = receivedAmount; }
    public void setStatus(String status) { this.status = status; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setJournalHeaderId(Long journalHeaderId) { this.journalHeaderId = journalHeaderId; }
}
