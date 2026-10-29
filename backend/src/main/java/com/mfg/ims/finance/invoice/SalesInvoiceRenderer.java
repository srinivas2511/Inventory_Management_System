package com.mfg.ims.finance.invoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.mfg.ims.finance.invoice.SalesInvoiceDto.ItemDto;

/**
 * Renders a sales invoice as a PDF suitable for sending to customers.
 * Uses the standard PDFBox Helvetica font (Latin characters only).
 */
@Component
public class SalesInvoiceRenderer {

    private static final float MARGIN = 40f;
    private static final float LINE = 14f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final NamedParameterJdbcTemplate jdbc;

    public SalesInvoiceRenderer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] render(SalesInvoiceDto inv) {
        String companyName = fetchCompanyName();
        String customerName = fetchCustomerName(inv.customerId());

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Page page = new Page(doc);
            page.heading("TAX INVOICE");
            page.pair("Invoice No.", inv.docNo());
            page.pair("Date", inv.docDate() != null ? DATE.format(inv.docDate()) : "");
            page.pair("Status", inv.status());
            page.pair("Company", companyName);
            page.pair("Customer", customerName);
            if (inv.placeOfSupply() != null && !inv.placeOfSupply().isBlank()) {
                page.pair("Place of Supply", inv.placeOfSupply());
            }
            if (inv.customerPoNo() != null && !inv.customerPoNo().isBlank()) {
                page.pair("Customer PO", inv.customerPoNo());
            }
            if (inv.irn() != null && !inv.irn().isBlank()) {
                page.pair("IRN", inv.irn());
            }
            page.gap();

            float[] cols = new float[]{25, 40, 175, 40, 30, 60, 60, 65};
            String[] head = new String[]{"#", "HSN/SAC", "Description", "Qty", "UOM", "Rate", "Tax", "Total (INR)"};
            page.tableRow(cols, head, true);

            if (inv.items() != null) {
                for (ItemDto item : inv.items()) {
                    page.tableRow(cols, new String[]{
                            String.valueOf(item.lineNo()),
                            safe(item.hsnSac()),
                            safe(item.description()),
                            qty(item.qty()),
                            safe(item.uom()),
                            money(item.rate()),
                            money(item.taxAmount()),
                            money(item.lineTotal())
                    }, false);
                }
            }

            page.gap();
            page.pair("Taxable Amount (INR)", money(inv.taxableAmount()));
            page.pair("Tax Amount (INR)", money(inv.taxAmount()));
            page.pair("Grand Total (INR)", money(inv.grandTotal()));
            if (inv.balanceInr() != null && inv.balanceInr().compareTo(BigDecimal.ZERO) > 0) {
                page.pair("Balance Due (INR)", money(inv.balanceInr()));
            }

            page.gap();
            page.text("This is a computer-generated invoice.");

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private String fetchCompanyName() {
        try {
            return jdbc.queryForObject("SELECT name FROM ims.companies WHERE id = 1", new MapSqlParameterSource(), String.class);
        } catch (Exception e) {
            return "Company";
        }
    }

    private String fetchCustomerName(Long customerId) {
        if (customerId == null) return "";
        try {
            return jdbc.queryForObject("SELECT name FROM ims.customers WHERE id = :id",
                    new MapSqlParameterSource("id", customerId), String.class);
        } catch (Exception e) {
            return "";
        }
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String qty(BigDecimal v) {
        if (v == null) return "0.000";
        return v.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    // ── Inner helper ──────────────────────────────────────────────────────────

    private static class Page {

        private final PDDocument doc;
        private final PDType1Font bold;
        private final PDType1Font regular;
        private PDPageContentStream cs;
        private float y;

        Page(PDDocument doc) throws IOException {
            this.doc = doc;
            this.bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            this.regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage();
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        void heading(String text) throws IOException {
            cs.beginText();
            cs.setFont(bold, 14);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(text);
            cs.endText();
            y -= LINE * 2;
        }

        void pair(String label, String value) throws IOException {
            ensureSpace();
            cs.beginText();
            cs.setFont(bold, 9);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(label + ": ");
            cs.setFont(regular, 9);
            cs.showText(value != null ? truncate(value, 80) : "");
            cs.endText();
            y -= LINE;
        }

        void text(String value) throws IOException {
            ensureSpace();
            cs.beginText();
            cs.setFont(regular, 8);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(truncate(value, 120));
            cs.endText();
            y -= LINE;
        }

        void gap() {
            y -= LINE / 2;
        }

        void tableRow(float[] cols, String[] cells, boolean header) throws IOException {
            ensureSpace();
            PDType1Font font = header ? bold : regular;
            float x = MARGIN;
            cs.beginText();
            cs.setFont(font, 8);
            for (int i = 0; i < cols.length && i < cells.length; i++) {
                cs.newLineAtOffset(i == 0 ? x : 0, 0);
                cs.showText(truncate(cells[i] != null ? cells[i] : "", (int) (cols[i] / 6)));
                x += cols[i];
            }
            cs.endText();
            y -= LINE;
        }

        private void ensureSpace() throws IOException {
            if (y < MARGIN + LINE * 3) newPage();
        }

        private static String truncate(String s, int max) {
            if (s == null) return "";
            s = s.replaceAll("[\\x00-\\x1F]", " ");
            return s.length() > max ? s.substring(0, max) : s;
        }
    }
}
