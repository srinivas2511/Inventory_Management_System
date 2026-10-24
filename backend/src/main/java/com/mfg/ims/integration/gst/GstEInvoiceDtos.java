package com.mfg.ims.integration.gst;

import java.time.Instant;

public final class GstEInvoiceDtos {

    private GstEInvoiceDtos() {}

    public record EInvoiceDto(
        Long id,
        Long dispatchId,
        String irn,
        String ackNo,
        Instant ackDate,
        String status,
        Instant generatedAt
    ) {}

    public record GenerateRequest(Long dispatchId) {}

    public record DownloadResponse(String payloadJson) {}
}
