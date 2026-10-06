package com.springmfg.ims.integration.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.integration.importer.RowReader.Table;

class RowReaderTest {

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsCsvWithBomQuotedCommasAndShortRows() throws Exception {
        String content = "﻿customerCode,name,phone\nC1,\"Acme, Inc\",123\n\nC2,Beta\n";
        Table t = RowReader.read(csv("c.csv", content), 100);

        assertThat(t.headers()).containsExactly("customerCode", "name", "phone");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(t.rows().get(0).values()).containsEntry("name", "Acme, Inc").containsEntry("phone", "123");
        assertThat(t.rows().get(1).rowNumber()).as("the blank line still counts").isEqualTo(4);
        assertThat(t.rows().get(1).values()).containsEntry("name", "Beta").containsEntry("phone", "");
    }

    @Test
    void readsXlsxFirstSheetAndSkipsBlankRows() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("materialCode");
            header.createCell(1).setCellValue("minStock");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("M-1");
            r1.createCell(1).setCellValue(12.5);
            sheet.createRow(2);                        // blank
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("M-2");
            r3.createCell(1).setCellValue(7);
            wb.createSheet("ignored").createRow(0).createCell(0).setCellValue("not read");
            wb.write(out);
            bytes = out.toByteArray();
        }
        Table t = RowReader.read(new MockMultipartFile("file", "m.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), 100);

        assertThat(t.headers()).containsExactly("materialCode", "minStock");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(t.rows().get(0).values()).containsEntry("materialCode", "M-1").containsEntry("minStock", "12.5");
        assertThat(t.rows().get(1).rowNumber()).as("the file's own row number, blank rows included").isEqualTo(4);
        assertThat(t.rows().get(1).values()).containsEntry("minStock", "7");
    }

    @Test
    void moreRowsThanTheLimitRejectTheFile() {
        StringBuilder sb = new StringBuilder("code\n");
        for (int i = 0; i < 6; i++) {
            sb.append("R").append(i).append('\n');
        }
        assertThatThrownBy(() -> RowReader.read(csv("big.csv", sb.toString()), 5))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("more than 5 data rows");
        // exactly at the limit is fine
        StringBuilder ok = new StringBuilder("code\n");
        for (int i = 0; i < 5; i++) {
            ok.append("R").append(i).append('\n');
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> RowReader.read(csv("ok.csv", ok.toString()), 5));
    }

    @Test
    void otherFileTypesAreRefused() {
        for (String name : new String[] {"data.xls", "data.txt", "data.csv.exe", "data", "../../etc/passwd"}) {
            assertThatThrownBy(() -> RowReader.read(csv(name, "a\n1\n"), 10))
                    .as(name).isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    @Test
    void aNonSpreadsheetNamedXlsxFailsCleanly() {
        MockMultipartFile fake = new MockMultipartFile("file", "x.xlsx", "application/octet-stream",
                "this is not a zip".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> RowReader.read(fake, 10)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("valid .xlsx");
    }
}
