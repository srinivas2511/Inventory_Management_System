package com.springmfg.ims.insight.report;

import java.time.LocalDate;

public record ReportRequest(LocalDate from, LocalDate to, Long entityId, String status, int page, int size) {
    public ReportRequest {
        if (size <= 0 || size > 500) size = 100;
        if (page < 0) page = 0;
    }
}
