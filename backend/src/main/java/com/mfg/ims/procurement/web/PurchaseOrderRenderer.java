package com.mfg.ims.procurement.web;

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

import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.procurement.dto.PurchaseOrderDto.PurchaseOrderItemDto;

@Component
public class PurchaseOrderRenderer {

    private static final float MARGIN = 40f;
    private static final float LINE = 14f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseOrderRenderer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] render(PurchaseOrderDto po) {
        String companyName = fetchCompanyName();
        String supplierName = po.supplierName() != null ? po.supplierName() : fetchSupplierName(po.supplierId());

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Page page = new Page(doc);
            page.heading("PURCHASE ORDER");
            page.pair("PO Number", po.poNumber());
            page.pair("Date", po.poDate() != null ? DATE.format(po.poDate()) : "");
            page.pair("Status", po.status());
            page.pair("Company", companyName);
            page.pair("Supplier", supplierName);
            if (po.expectedDeliveryDate() != null) {
                page.pair("Expected Delivery", DATE.format(po.expectedDeliveryDate()));
            }
            if (po.paymentTerms() != null && !po.paymentTerms().isBlank()) {
                page.pair("Payment Terms", po.paymentTerms());
            }
            if (po.currency() != null && !po.currency().equals("INR")) {
                page.pair("Currency", po.currency());
            }
            page.gap();

            float[] cols = new float[]{20, 50, 185, 50, 35, 65, 70};
            page.tableRow(cols, new String[]{"#", "Material Code", "Material", "Qty", "UOM", "Rate (INR)", "Total (INR)"}, true);

            if (po.items() != null) {
                for (PurchaseOrderItemDto item : po.items()) {
                    page.tableRow(cols, new String[]{
                            safe(item.lineNo()),
                            safe(item.materialCode()),
                            safe(item.materialName()),
                            qty(item.orderedQty()),
                            safe(item.uom()),
                            money(item.rate()),
                            money(item.lineTotal())
                    }, false);
                }
            }

            page.gap();
            if (po.subtotal() != null) page.pair("Subtotal (INR)", money(po.subtotal()));
            if (po.taxTotal() != null) page.pair("Tax Total (INR)", money(po.taxTotal()));
            page.pair("Grand Total (INR)", money(po.grandTotal()));
            if (po.remarks() != null && !po.remarks().isBlank()) {
                page.gap();
                page.pair("Remarks", po.remarks());
            }
            page.gap();
            page.text("This is a computer-generated purchase order.");

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

    private String fetchSupplierName(Long supplierId) {
        if (supplierId == null) return "";
        try {
            return jdbc.queryForObject("SELECT name FROM ims.suppliers WHERE id = :id",
                    new MapSqlParameterSource("id", supplierId), String.class);
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

    private static String safe(Object v) {
        return v != null ? v.toString() : "";
    }

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
