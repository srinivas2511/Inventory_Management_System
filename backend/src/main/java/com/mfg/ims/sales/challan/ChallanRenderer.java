package com.mfg.ims.sales.challan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * 11.5: Renders a delivery challan as a printable PDF document.
 * Follows the same pattern as DispatchDocumentRenderer (PDFBox, standard Helvetica font).
 * Pure rendering: reads no database and holds no state.
 */
@Component
public class ChallanRenderer {

    private static final float MARGIN = 40f;
    private static final float LINE = 14f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    public byte[] render(ChallanDto challan, String customerName, String customerAddress) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Page page = new Page(doc);
            page.heading("DELIVERY CHALLAN");
            page.pair("Challan No.", challan.docNo());
            page.pair("Date", challan.docDate() != null ? DATE.format(challan.docDate()) : "");
            page.pair("Kind", challan.challanKind());
            page.pair("Customer", safe(customerName));
            if (customerAddress != null && !customerAddress.isBlank()) {
                page.pair("Address", safe(customerAddress));
            }
            if (challan.deliveryAddress() != null && !challan.deliveryAddress().isBlank()) {
                page.pair("Deliver to", safe(challan.deliveryAddress()));
            }
            if (challan.vehicleNo() != null && !challan.vehicleNo().isBlank()) {
                page.pair("Vehicle No.", safe(challan.vehicleNo()));
            }
            if (challan.lrNo() != null && !challan.lrNo().isBlank()) {
                page.pair("LR No.", safe(challan.lrNo()));
            }
            if (challan.isReturnable()) {
                page.pair("Returnable", "Yes");
                if (challan.expectedReturnDate() != null) {
                    page.pair("Expected Return", DATE.format(challan.expectedReturnDate()));
                }
            }
            page.gap();

            float[] cols = new float[]{ 25, 130, 65, 45, 65, 65 };
            String[] head = new String[]{ "#", "Description", "Qty", "UOM", "Rate", "Value" };
            page.tableRow(cols, head, true);

            BigDecimal totalValue = BigDecimal.ZERO;
            for (var item : challan.items()) {
                String[] cells = new String[]{
                    String.valueOf(item.lineNo()),
                    safe(item.description() != null ? item.description() : ""),
                    qty(item.qty()),
                    safe(item.uom() != null ? item.uom() : ""),
                    money(item.rate()),
                    money(item.lineTotal())
                };
                page.tableRow(cols, cells, false);
                if (item.lineTotal() != null) totalValue = totalValue.add(item.lineTotal());
            }
            page.gap();
            page.pair("Total Value", money(totalValue.compareTo(BigDecimal.ZERO) == 0 && challan.grandTotal() != null
                    ? challan.grandTotal() : totalValue));
            if (challan.remarks() != null && !challan.remarks().isBlank()) {
                page.pair("Remarks", safe(challan.remarks()));
            }
            page.gap();
            page.pair("Receiver's Signature", "______________");
            page.pair("Date of Receipt", "______________");
            page.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the challan PDF", e);
        }
    }

    static String safe(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            b.append(c >= 32 && c <= 126 ? c : (c >= 160 && c <= 255 ? c : '?'));
        }
        return b.toString();
    }

    private static String qty(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

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
            if (cs != null) cs.close();
            PDPage p = new PDPage(PDRectangle.A4);
            doc.addPage(p);
            cs = new PDPageContentStream(doc, p);
            y = p.getMediaBox().getHeight() - MARGIN;
        }

        private void ensureRoom() throws IOException {
            if (y < MARGIN + LINE) newPage();
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
