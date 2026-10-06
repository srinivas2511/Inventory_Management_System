package com.springmfg.ims.integration.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExportServiceTest {

    @Test
    void cellsASpreadsheetWouldRunAsAFormulaAreDefused() {
        assertThat(ExportService.csvCell("=SUM(A1:A9)")).isEqualTo("'=SUM(A1:A9)");
        assertThat(ExportService.csvCell("+1+1")).isEqualTo("'+1+1");
        assertThat(ExportService.csvCell("-2+3")).isEqualTo("'-2+3");
        assertThat(ExportService.csvCell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(ExportService.csvCell("\t=cmd")).isEqualTo("'\t=cmd");
        assertThat(ExportService.csvCell("=cmd|' /C calc'!A0")).startsWith("'=");
    }

    @Test
    void ordinaryValuesAndRealNumbersAreLeftAlone() {
        assertThat(ExportService.csvCell("Acme Springs")).isEqualTo("Acme Springs");
        assertThat(ExportService.csvCell("a=b")).isEqualTo("a=b");
        assertThat(ExportService.csvCell(null)).isEmpty();
        assertThat(ExportService.csvCell(new BigDecimal("-12.5000"))).as("a negative number is not a formula")
                .isEqualTo("-12.5000");
        assertThat(ExportService.csvCell(-3)).isEqualTo("-3");
        assertThat(ExportService.csvCell(true)).isEqualTo("true");
    }

    @Test
    void datesAndTimestampsBecomeIsoText() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("day", Date.valueOf(LocalDate.of(2026, 10, 5)));
        row.put("at", Timestamp.from(Instant.parse("2026-10-05T08:30:00Z")));
        row.put("name", "x");
        Map<String, Object> out = ExportService.normalise(row);
        assertThat(out).containsExactly(
                Map.entry("id", 7L), Map.entry("day", "2026-10-05"),
                Map.entry("at", "2026-10-05T08:30:00Z"), Map.entry("name", "x"));
    }
}
