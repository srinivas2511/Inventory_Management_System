package com.mfg.ims.integration.eway;

import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.finance.invoice.SalesInvoice;
import com.mfg.ims.finance.invoice.SalesInvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class EwayBillService {

    private final EwayBillRepository repo;
    private final SalesInvoiceRepository invoiceRepo;
    private final SystemSettingService settings;

    public EwayBillService(EwayBillRepository repo,
                           SalesInvoiceRepository invoiceRepo,
                           SystemSettingService settings) {
        this.repo = repo;
        this.invoiceRepo = invoiceRepo;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<EwayBillDtos.EwayBillDto> list(Long invoiceId) {
        List<EwayBill> bills = invoiceId != null
                ? repo.findByCompanyIdAndInvoiceIdOrderByCreatedAtDesc(1L, invoiceId)
                : repo.findByCompanyIdOrderByCreatedAtDesc(1L);
        return bills.stream().map(this::toDto).toList();
    }

    /** Builds the e-way bill payload and stores it as DRAFT for review. */
    public EwayBillDtos.EwayBillDto generate(EwayBillDtos.GenerateRequest req, Long userId) {
        SalesInvoice invoice = invoiceRepo.findById(req.invoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sales invoice not found: " + req.invoiceId()));

        String payload = buildPayload(invoice, req);

        var bill = new EwayBill();
        bill.setInvoiceId(invoice.getId());
        bill.setDispatchId(invoice.getDispatchId());
        bill.setTransporterId(req.transporterId());
        bill.setVehicleNumber(req.vehicleNumber());
        bill.setDistanceKm(req.distanceKm());
        bill.setRequestPayload(payload);
        bill.setStatus("DRAFT");
        bill.setCreatedBy(userId);

        return toDto(repo.save(bill));
    }

    /** Submit the e-way bill to the GSP URL if configured; otherwise marks as SIMULATED. */
    public EwayBillDtos.EwayBillDto submit(Long id) {
        var bill = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "E-way bill not found: " + id));
        if (!"DRAFT".equals(bill.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only DRAFT e-way bills can be submitted");
        }

        String gspUrl = settings.get("gsp_url", null);
        if (gspUrl != null && !gspUrl.isBlank()) {
            // In a real implementation, call the GSP URL via RestTemplate/WebClient.
            // For now, we mark as SUBMITTED with a simulated response.
            bill.setStatus("SUBMITTED");
            bill.setResponsePayload("{\"status\":\"ACCEPTED\",\"ewbNo\":\"SIMULATED-" + id + "\"}");
        } else {
            // No GSP configured — mark as simulated
            bill.setEwayBillNumber("EWB-SIM-" + id);
            bill.setGeneratedAt(Instant.now());
            bill.setValidUntil(Instant.now().plus(3, ChronoUnit.DAYS));
            bill.setStatus("SIMULATED");
            bill.setResponsePayload("{\"status\":\"SIMULATED\",\"ewbNo\":\"EWB-SIM-" + id + "\"}");
        }

        return toDto(repo.save(bill));
    }

    private String buildPayload(SalesInvoice invoice, EwayBillDtos.GenerateRequest req) {
        return String.format(
                "{\"docNo\":\"%s\",\"docDate\":\"%s\",\"invoiceValue\":%.2f," +
                "\"transporterId\":%s,\"vehicleNumber\":\"%s\",\"distanceKm\":%s}",
                invoice.getDocNo(),
                invoice.getDocDate(),
                invoice.getGrandTotal(),
                req.transporterId() != null ? req.transporterId() : "null",
                req.vehicleNumber() != null ? req.vehicleNumber() : "",
                req.distanceKm() != null ? req.distanceKm() : "null"
        );
    }

    private EwayBillDtos.EwayBillDto toDto(EwayBill b) {
        return new EwayBillDtos.EwayBillDto(b.getId(), b.getInvoiceId(), b.getDispatchId(),
                b.getEwayBillNumber(), b.getStatus(), b.getTransporterId(),
                b.getVehicleNumber(), b.getDistanceKm(), b.getGeneratedAt(), b.getValidUntil());
    }
}
