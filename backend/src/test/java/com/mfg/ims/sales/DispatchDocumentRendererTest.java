package com.mfg.ims.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mfg.ims.sales.dto.DispatchNoteDto;
import com.mfg.ims.sales.dto.DispatchNoteDto.DispatchNoteLineDto;
import com.mfg.ims.sales.service.DispatchDocumentRenderer;

/**
 * GC-4 / SAL-005: the delivery note and the packing list are real PDFs that carry the dispatch data. The PDFs are
 * parsed back to text, so the assertions are on what a person would read on the printed page.
 */
class DispatchDocumentRendererTest {

    private final DispatchDocumentRenderer renderer = new DispatchDocumentRenderer();

    private static DispatchNoteDto note(int lines, String customerName) {
        List<DispatchNoteLineDto> l = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (int i = 1; i <= lines; i++) {
            BigDecimal qty = new BigDecimal("60.000");
            BigDecimal rate = new BigDecimal("125.5000");
            BigDecimal value = qty.multiply(rate);
            l.add(new DispatchNoteLineDto(i, "SPR-" + String.format("%03d", i), "Compression spring " + i,
                    "FGB/26/" + String.format("%05d", i), qty, "PCS", rate, value, "CP-" + i, "DRG-" + i));
            totalQty = totalQty.add(qty);
            totalValue = totalValue.add(value);
        }
        return new DispatchNoteDto("DSP/26/00007", "SO/26/00012", customerName, "27AAAPL1234C1ZV",
                LocalDate.of(2026, 10, 9), "Fast Freight", "MH12AB1234", "LR-889", "Plot 4, MIDC, Pune",
                "INV-2026-77", l, totalQty, totalValue);
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("SAL-005: the delivery note is a PDF with the header, the lines, the prices and the totals")
    void deliveryNoteCarriesTheDispatchData() throws Exception {
        byte[] pdf = renderer.deliveryNote(note(2, "Acme Springs Pvt Ltd"));
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String t = text(pdf);
        assertThat(t).contains("DELIVERY NOTE", "DSP/26/00007", "SO/26/00012", "Acme Springs Pvt Ltd",
                "27AAAPL1234C1ZV", "09-Oct-2026", "Fast Freight", "MH12AB1234", "INV-2026-77",
                "SPR-001", "FGB/26/00002", "Rate (INR)", "125.50");
        // 2 lines x 60 pcs = 120 pcs; 2 x (60 x 125.50 = 7,530.00) = 15,060.00
        assertThat(t).contains("Total quantity").contains("120");
        assertThat(t).contains("Total value (INR)").contains("15060.00");
        assertThat(t).contains("Received by");
    }

    @Test
    @DisplayName("SAL-005: the packing list shows batches and quantities without prices, with blanks for the packer")
    void packingListShowsBatchesWithoutPrices() throws Exception {
        String t = text(renderer.packingList(note(2, "Acme Springs Pvt Ltd")));
        assertThat(t).contains("PACKING LIST", "DSP/26/00007", "SPR-002", "FGB/26/00001", "CP-1", "Total quantity");
        assertThat(t).contains("No. of packages", "Gross weight (kg)", "Packed by", "Checked by");
        assertThat(t).doesNotContain("Rate (INR)").doesNotContain("Total value").doesNotContain("125.50");
    }

    @Test
    @DisplayName("SAL-005: a long dispatch runs onto further pages and every line is printed")
    void aLongDispatchPaginates() throws Exception {
        byte[] pdf = renderer.deliveryNote(note(70, "Acme Springs Pvt Ltd"));
        assertThat(pages(pdf)).isGreaterThan(1);
        String t = text(pdf);
        assertThat(t).contains("SPR-001", "SPR-035", "SPR-070", "FGB/26/00070");
    }

    @Test
    @DisplayName("SAL-005: text the standard font cannot print becomes '?' and never fails the document")
    void unprintableCharactersDoNotBreakTheDocument() throws Exception {
        byte[] pdf = renderer.deliveryNote(note(1, "Shri Ganesh श्री Springs ₹"));
        String t = text(pdf);
        assertThat(t).contains("Shri Ganesh").contains("Springs");
        assertThat(t).doesNotContain("₹");
    }
}
