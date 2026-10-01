package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.numbering.DocumentType;

/** The formats of DESIGN.md section 1.3, example for example. */
class DocumentTypeTest {

    private static final LocalDate OCT_2026 = LocalDate.of(2026, 10, 1);

    @Test
    void producesTheDocumentedExamples() {
        assertThat(DocumentType.PURCHASE_REQUISITION.format(OCT_2026, 18)).isEqualTo("PR-2026-00018");
        assertThat(DocumentType.PURCHASE_ORDER.format(OCT_2026, 31)).isEqualTo("PUR-2026-00031");
        assertThat(DocumentType.GOODS_RECEIPT.format(OCT_2026, 44)).isEqualTo("GR-2026-00044");
        assertThat(DocumentType.RM_BATCH.format(OCT_2026, 1)).isEqualTo("RM-BATCH-2026-001");
        assertThat(DocumentType.PRODUCTION_ORDER.format(OCT_2026, 125)).isEqualTo("PO-2026-00125");
        assertThat(DocumentType.MATERIAL_ISSUE.format(OCT_2026, 210)).isEqualTo("MI-2026-00210");
        assertThat(DocumentType.FG_BATCH.format(OCT_2026, 50)).isEqualTo("FG-2026-0050");
        assertThat(DocumentType.INSPECTION.format(OCT_2026, 71)).isEqualTo("QI-2026-00071");
        assertThat(DocumentType.STOCK_TRANSACTION.format(OCT_2026, 412)).isEqualTo("TXN-202610-000412");
        assertThat(DocumentType.STOCK_ADJUSTMENT.format(OCT_2026, 9)).isEqualTo("ADJ-2026-00009");
        assertThat(DocumentType.SALES_ORDER.format(OCT_2026, 52)).isEqualTo("SO-2026-00052");
        assertThat(DocumentType.DISPATCH.format(OCT_2026, 33)).isEqualTo("DSP-2026-00033");
        assertThat(DocumentType.BOM.format(OCT_2026, 1)).isEqualTo("BOM-001");
    }

    @Test
    void numbersGrowPastTheirWidthInsteadOfTruncating() {
        assertThat(DocumentType.FG_BATCH.format(OCT_2026, 12345)).isEqualTo("FG-2026-12345");
        assertThat(DocumentType.BOM.format(OCT_2026, 1000)).isEqualTo("BOM-1000");
    }

    @Test
    void sequencesRestartPerYearMonthOrNever() {
        assertThat(DocumentType.PURCHASE_ORDER.periodKey(LocalDate.of(2026, 12, 31))).isEqualTo(2026);
        assertThat(DocumentType.PURCHASE_ORDER.periodKey(LocalDate.of(2027, 1, 1))).isEqualTo(2027);
        assertThat(DocumentType.STOCK_TRANSACTION.periodKey(LocalDate.of(2026, 10, 31))).isEqualTo(202610);
        assertThat(DocumentType.STOCK_TRANSACTION.periodKey(LocalDate.of(2026, 11, 1))).isEqualTo(202611);
        assertThat(DocumentType.BOM.periodKey(LocalDate.of(2026, 10, 1))).isZero();
        assertThat(DocumentType.BOM.periodKey(LocalDate.of(2040, 1, 1))).isZero();
    }

    @Test
    void everyTypeHasAUniquePrefixThatFitsTheColumn() {
        long distinct = java.util.Arrays.stream(DocumentType.values()).map(DocumentType::prefix).distinct().count();
        assertThat(distinct).isEqualTo(DocumentType.values().length);
        for (DocumentType type : DocumentType.values()) {
            assertThat(type.prefix().length()).isLessThanOrEqualTo(20); // number_sequences.prefix VARCHAR(20)
        }
    }
}
