package com.springmfg.ims.sales.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import com.springmfg.ims.sales.dto.DispatchNoteDto;
import com.springmfg.ims.sales.dto.DispatchNoteDto.DispatchNoteLineDto;

/**
 * SAL-005 / GC-4: renders the dispatch note as the two printable PDF documents that travel with a consignment - the
 * delivery note (what was sent, with prices) and the packing list (what is in the load, by batch, with blank fields
 * for the packer to complete). Pure rendering of {@link DispatchNoteDto}: it reads no database and has no state.
 * Text uses the standard Helvetica font, which covers Latin text only, so characters outside it print as '?'.
 */
@Component
public class DispatchDocumentRenderer {

    private static final float MARGIN = 40f;
    private static final float LINE = 14f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);

    public byte[] deliveryNote(DispatchNoteDto note) {
        return render(note, false);
    }

    public byte[] packingList(DispatchNoteDto note) {
        return render(note, true);
    }

    private byte[] render(DispatchNoteDto note, boolean packing) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Page page = new Page(doc);
            page.heading(packing ? "PACKING LIST" : "DELIVERY NOTE");
            page.pair("Dispatch no.", note.dispatchNumber());
            page.pair("Sales order", note.orderNumber());
            page.pair("Date", note.dispatchDate() != null ? DATE.format(note.dispatchDate()) : "");
            page.pair("Customer", note.customerName());
            if (note.customerGst() != null && !note.customerGst().isBlank()) {
                page.pair("Customer GSTIN", note.customerGst());
            }
            page.pair("Deliver to", note.deliveryAddress());
            page.pair("Transporter", note.transporter());
            page.pair("Vehicle / LR no.", join(note.vehicleNo(), note.lrNumber()));
            if (!packing && note.invoiceReference() != null && !note.invoiceReference().isBlank()) {
                page.pair("Invoice ref.", note.invoiceReference());
            }
            page.gap();

            float[] cols = packing
                    ? new float[] { 25, 80, 160, 70, 70, 40, 60 }
                    : new float[] { 25, 75, 140, 65, 55, 35, 60, 60 };
            String[] head = packing
                    ? new String[] { "#", "Product", "Description", "Batch", "Qty", "UOM", "Cust. part" }
                    : new String[] { "#", "Product", "Description", "Batch", "Qty", "UOM", "Rate (INR)", "Value (INR)" };
            page.tableRow(cols, head, true);
            List<DispatchNoteLineDto> lines = note.lines();
            for (DispatchNoteLineDto l : lines) {
                String[] cells = packing
                        ? new String[] { String.valueOf(l.seq()), l.productCode(), l.productName(), l.batchNumber(),
                                qty(l.quantity()), l.uom(), l.customerPartNumber() }
                        : new String[] { String.valueOf(l.seq()), l.productCode(), l.productName(), l.batchNumber(),
                                qty(l.quantity()), l.uom(), money(l.unitPrice()), money(l.lineValue()) };
                page.tableRow(cols, cells, false);
            }
            page.gap();
            page.pair("Total quantity", qty(note.totalQty()));
            if (packing) {
                page.gap();
                page.pair("No. of packages", "______________");
                page.pair("Gross weight (kg)", "______________");
                page.pair("Packed by", "______________");
                page.pair("Checked by", "______________");
            } else {
                page.pair("Total value (INR)", money(note.totalValue()));
                page.gap();
                page.pair("Received by", "______________");
                page.pair("Signature / date", "______________");
            }
            page.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the dispatch document", e);
        }
    }

    private static String join(String a, String b) {
        String x = a == null ? "" : a;
        String y = b == null ? "" : b;
        return x.isEmpty() || y.isEmpty() ? x + y : x + " / " + y;
    }

    private static String qty(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Standard Helvetica encodes Latin-1 only; anything else would make PDFBox throw, so print it as '?'. */
    static String safe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            b.append(c >= 32 && c <= 126 ? c : (c >= 160 && c <= 255 ? c : '?'));
        }
        return b.toString();
    }

    /** Minimal top-to-bottom writer that starts a new page when the current one is full. */
    private static final class Page {
        private final PDDocument doc;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream cs;
        private float y;

        Page(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage p = new PDPage(PDRectangle.A4);
            doc.addPage(p);
            cs = new PDPageContentStream(doc, p);
            y = p.getMediaBox().getHeight() - MARGIN;
        }

        private void ensureRoom() throws IOException {
            if (y < MARGIN + LINE) {
                newPage();
            }
        }

        private void text(float x, String s, boolean isBold, float size) throws IOException {
            cs.beginText();
            cs.setFont(isBold ? bold : regular, size);
            cs.newLineAtOffset(x, y);
            cs.showText(safe(s));
            cs.endText();
        }

        void heading(String s) throws IOException {
            text(MARGIN, s, true, 16);
            y -= 26;
        }

        void pair(String label, String value) throws IOException {
            ensureRoom();
            text(MARGIN, label, true, 10);
            text(MARGIN + 110, value, false, 10);
            y -= LINE;
        }

        void gap() {
            y -= LINE / 2;
        }

        void tableRow(float[] widths, String[] cells, boolean header) throws IOException {
            ensureRoom();
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                String c = cells[i] == null ? "" : cells[i];
                // keep each cell inside its column: ~5 points per character at 9pt
                int max = Math.max(1, (int) (widths[i] / 5f));
                text(x, c.length() > max ? c.substring(0, max) : c, header, 9);
                x += widths[i];
            }
            y -= LINE;
        }

        void close() throws IOException {
            cs.close();
        }
    }
}
