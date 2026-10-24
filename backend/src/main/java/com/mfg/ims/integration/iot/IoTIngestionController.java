package com.mfg.ims.integration.iot;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.integration.iot.IoTIngestionService.BulkIngestionRequest;
import com.mfg.ims.integration.iot.IoTIngestionService.BulkIngestionResponse;

/**
 * HTTP endpoint for edge device / gateway ingestion (R19-6.3 INT-006).
 * Edge devices POST batches of machine sensor and event data here.
 */
@RestController
@RequestMapping("/api/integration/iot")
public class IoTIngestionController {

    private final IoTIngestionService service;

    public IoTIngestionController(IoTIngestionService service) {
        this.service = service;
    }

    @PostMapping("/events")
    @PreAuthorize("hasAuthority('IOT_INGEST')")
    public ResponseEntity<BulkIngestionResponse> ingestEvents(@RequestBody BulkIngestionRequest req) {
        return ResponseEntity.ok(service.ingest(req.events()));
    }
}
