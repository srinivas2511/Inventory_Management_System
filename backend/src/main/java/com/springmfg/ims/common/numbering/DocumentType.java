package com.springmfg.ims.common.numbering;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Business document number formats (DESIGN.md section 1.3). The sequence restarts each {@link Period}; the
 * counter is stored in {@code number_sequences} under {@code (prefix, periodKey)}.
 */
public enum DocumentType {

    PURCHASE_REQUISITION("PR", Period.YEAR, 5),
    PURCHASE_ORDER("PUR", Period.YEAR, 5),
    GOODS_RECEIPT("GR", Period.YEAR, 5),
    RM_BATCH("RM-BATCH", Period.YEAR, 3),
    PRODUCTION_ORDER("PO", Period.YEAR, 5),
    MATERIAL_ISSUE("MI", Period.YEAR, 5),
    FG_BATCH("FG", Period.YEAR, 4),
    INSPECTION("QI", Period.YEAR, 5),
    STOCK_TRANSACTION("TXN", Period.MONTH, 6),
    STOCK_ADJUSTMENT("ADJ", Period.YEAR, 5),
    SALES_ORDER("SO", Period.YEAR, 5),
    DISPATCH("DSP", Period.YEAR, 5),
    /** {@code BOM-001}; the revision letter is not part of the number. */
    BOM("BOM", Period.NONE, 3);

    /** When the sequence restarts at 1. */
    public enum Period {
        YEAR, MONTH, NONE
    }

    private final String prefix;
    private final Period period;
    private final int width;

    DocumentType(String prefix, Period period, int width) {
        this.prefix = prefix;
        this.period = period;
        this.width = width;
    }

    public String prefix() {
        return prefix;
    }

    public Period period() {
        return period;
    }

    /** Value of {@code number_sequences.seq_year}: 2026, 202610, or 0 for a sequence that never restarts. */
    public int periodKey(LocalDate date) {
        return switch (period) {
            case YEAR -> date.getYear();
            case MONTH -> date.getYear() * 100 + date.getMonthValue();
            case NONE -> 0;
        };
    }

    /** The document number for sequence value {@code value} in the period containing {@code date}. */
    public String format(LocalDate date, long value) {
        String number = String.format(Locale.ROOT, "%0" + width + "d", value); // grows past the width, never truncates
        return period == Period.NONE ? prefix + "-" + number : prefix + "-" + periodKey(date) + "-" + number;
    }
}
