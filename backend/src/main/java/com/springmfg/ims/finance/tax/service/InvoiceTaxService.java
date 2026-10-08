package com.springmfg.ims.finance.tax.service;

import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.tax.domain.InvoiceTaxLine;
import com.springmfg.ims.finance.tax.dto.TaxDtos.TaxLineDto;
import com.springmfg.ims.finance.tax.repository.InvoiceTaxLineRepository;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Component;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Determination;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists the tax lines of a document and turns them into journal lines. */
@Service
@Transactional
public class InvoiceTaxService {

    private final InvoiceTaxLineRepository lines;

    public InvoiceTaxService(InvoiceTaxLineRepository lines) {
        this.lines = lines;
    }

    public void record(String documentType, Long documentId, Direction direction, Determination determination) {
        for (Component c : determination.components()) {
            InvoiceTaxLine line = new InvoiceTaxLine();
            line.setDocumentType(documentType);
            line.setDocumentId(documentId);
            line.setDirection(direction.name());
            line.setSupplyType(determination.supplyType());
            line.setTaxCodeId(c.taxCodeId());
            line.setTaxCode(c.taxCode());
            line.setTaxType(c.taxType());
            line.setRatePct(c.ratePct());
            line.setTaxableAmount(c.taxableAmount());
            line.setTaxAmount(c.taxAmount());
            line.setGlAccountId(c.glAccountId());
            lines.save(line);
        }
    }

    @Transactional(readOnly = true)
    public List<TaxLineDto> lines(String documentType, Long documentId) {
        return entities(documentType, documentId).stream()
                .map(l -> new TaxLineDto(l.getTaxCode(), l.getTaxType(), l.getRatePct(), l.getTaxableAmount(), l.getTaxAmount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceTaxLine> entities(String documentType, Long documentId) {
        return lines.findByDocumentTypeAndDocumentIdOrderByIdAsc(documentType, documentId);
    }

    /**
     * One journal line per GL account for the stored tax of a document, zero amounts skipped. {@code debit} is true for
     * input tax on a purchase and false for output tax on a sale.
     */
    @Transactional(readOnly = true)
    public List<JournalLineRequest> journalLines(String documentType, Long documentId, boolean debit, String label) {
        return journalLines(documentType, documentId, debit, label, BigDecimal.ONE);
    }

    /** As above for a foreign-currency document: each GL account's tax is converted to INR at {@code rate} once. */
    @Transactional(readOnly = true)
    public List<JournalLineRequest> journalLines(String documentType, Long documentId, boolean debit, String label,
                                                 BigDecimal rate) {
        Map<Long, BigDecimal> byAccount = new LinkedHashMap<>();
        for (InvoiceTaxLine l : entities(documentType, documentId)) {
            if (l.getTaxAmount().signum() > 0) {
                byAccount.merge(l.getGlAccountId(), l.getTaxAmount(), BigDecimal::add);
            }
        }
        byAccount.replaceAll((account, amount) -> amount.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP));
        List<JournalLineRequest> out = new ArrayList<>();
        byAccount.forEach((account, amount) -> out.add(debit
                ? new JournalLineRequest(account, amount, BigDecimal.ZERO, label)
                : new JournalLineRequest(account, BigDecimal.ZERO, amount, label)));
        return out;
    }
}
