package com.mfg.ims.finance.fx.dto;

import java.math.BigDecimal;

public class FxRevaluationDtos {

    /** Summary returned after a revaluation run. */
    public record FxRevaluationRunResult(
            int apCount,
            int arCount,
            BigDecimal apGainInr,
            BigDecimal apLossInr,
            BigDecimal arGainInr,
            BigDecimal arLossInr
    ) {}

    /** One revaluation row for a (document, period) pair. */
    public record FxRevaluationDto(
            Long id,
            Long periodId,
            String periodName,
            String documentType,
            Long documentId,
            String documentNumber,
            String currencyCode,
            BigDecimal remainingForeign,
            BigDecimal bookingRate,
            BigDecimal closingRate,
            BigDecimal differenceInr,
            Long journalHeaderId
    ) {}
}
