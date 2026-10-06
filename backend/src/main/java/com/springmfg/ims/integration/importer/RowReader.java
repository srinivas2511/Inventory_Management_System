package com.springmfg.ims.integration.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/** Reads the header row and data rows of a CSV or XLSX upload into string maps. Row numbers are the file's own. */
final class RowReader {

    record SourceRow(int rowNumber, Map<String, String> values) {
    }

    record Table(List<String> headers, List<SourceRow> rows) {
    }

    private RowReader() {
    }

    static Table read(MultipartFile file, int maxRows) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean csv = name.endsWith(".csv");
        if (!csv && !name.endsWith(".xlsx")) {
            throw new BusinessRuleException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "Only .csv and .xlsx files can be imported");
        }
        try {
            return csv ? csv(file, maxRows) : xlsx(file, maxRows);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // a corrupt workbook, an unterminated quote, an encrypted file: the user's file is at fault, not the server
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The file could not be read as a valid " + (csv ? ".csv" : ".xlsx") + " file");
        }
    }

    private static Table csv(MultipartFile file, int maxRows) throws IOException {
        try (InputStream in = file.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            List<String> headers = new ArrayList<>();
            for (String h : parser.getHeaderNames()) {
                headers.add(stripBom(h));
            }
            List<SourceRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= maxRows) {
                    throw tooMany(maxRows);
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    values.put(headers.get(i), i < record.size() ? record.get(i) : "");
                }
                rows.add(new SourceRow((int) parser.getCurrentLineNumber(), values));
            }
            return new Table(headers, rows);
        }
    }

    private static Table xlsx(MultipartFile file, int maxRows) throws IOException {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The first sheet is empty");
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            List<SourceRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String v = formatter.formatCellValue(row.getCell(c)).trim();
                    any |= !v.isEmpty();
                    values.put(headers.get(c), v);
                }
                if (!any) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw tooMany(maxRows);
                }
                rows.add(new SourceRow(r + 1, values));
            }
            return new Table(headers, rows);
        }
    }

    private static String stripBom(String s) {
        return s != null && !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    private static BusinessRuleException tooMany(int maxRows) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "The file has more than " + maxRows + " data rows; split it into smaller files");
    }
}
