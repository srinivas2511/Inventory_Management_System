package com.springmfg.ims.oee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response shapes of the OEE endpoints. Ratios are fractions (0.8523 is 85.23%); times are minutes. */
public final class OeeDtos {

    private OeeDtos() {
    }

    public record OeeDto(
            Long machineId,
            String machineCode,
            String machineName,
            LocalDate from,
            LocalDate to,
            BigDecimal availableMinutes,
            BigDecimal plannedDowntimeMinutes,
            BigDecimal unplannedDowntimeMinutes,
            BigDecimal operatingMinutes,
            BigDecimal idealMinutes,
            BigDecimal goodQty,
            BigDecimal badQty,
            BigDecimal availability,
            BigDecimal performance,
            BigDecimal quality,
            BigDecimal oee) {
    }

    public record OeeTrendPointDto(
            LocalDate day,
            BigDecimal availableMinutes,
            BigDecimal operatingMinutes,
            BigDecimal goodQty,
            BigDecimal badQty,
            BigDecimal availability,
            BigDecimal performance,
            BigDecimal quality,
            BigDecimal oee) {
    }

    public record OeeLossDto(String category, String label, BigDecimal minutes, BigDecimal share,
                             BigDecimal cumulativeShare) {
    }

    public record OeeLossesDto(LocalDate from, LocalDate to, BigDecimal totalLossMinutes, List<OeeLossDto> losses) {
    }

    public record DowntimeReasonDto(String reason, String category, long occurrences, BigDecimal minutes) {
    }
}
