package com.springmfg.ims.qualityplus.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.springmfg.ims.qualityplus.domain.ComplaintCategory;
import com.springmfg.ims.qualityplus.domain.ComplaintDisposition;
import com.springmfg.ims.qualityplus.domain.ComplaintStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ComplaintDtos {

    private ComplaintDtos() {
    }

    public record SaveComplaintRequest(
            @NotNull Long customerId,
            Long productId,
            Long fgBatchId,
            @NotNull LocalDate complaintDate,
            @NotNull ComplaintCategory category,
            @NotBlank String description,
            @Size(max = 60) String customerRef,
            @PositiveOrZero BigDecimal quantityComplained) {
    }

    /** Resolving needs a disposition and notes; the returned quantity is informational. */
    public record TransitionComplaintRequest(
            @NotNull ComplaintStatus target,
            ComplaintDisposition disposition,
            @PositiveOrZero BigDecimal quantityReturned,
            @Size(max = 1000) String resolutionNotes) {
    }

    /** Customer picker entry for the complaint form (quality staff cannot read the customer master). */
    public record LookupDto(Long id, String code, String name) {
    }

    public record ComplaintSummaryDto(
            Long id, String complaintNumber, String customerName, String productCode, String fgBatchNumber,
            LocalDate complaintDate, ComplaintCategory category, ComplaintStatus status, String capaNumber) {
    }

    public record ComplaintDto(
            Long id, String complaintNumber, Long customerId, String customerName, Long productId,
            String productCode, String productName, Long fgBatchId, String fgBatchNumber, LocalDate complaintDate,
            Long receivedBy, ComplaintCategory category, String description, String customerRef,
            BigDecimal quantityComplained, BigDecimal quantityReturned, ComplaintDisposition disposition,
            String resolutionNotes, ComplaintStatus status, Set<ComplaintStatus> nextStatuses, Long capaId,
            String capaNumber, Instant closedAt, Instant createdAt) {
    }
}
