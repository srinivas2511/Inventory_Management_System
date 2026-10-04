package com.springmfg.ims.finance.ar.dto;

import java.util.List;

/** Outcome of one retry run over the dispatches that had no AR invoice. */
public record ArReconcileResultDto(int attempted, int created, int alreadyInvoiced, int failed,
                                   List<Failure> failures) {

    public record Failure(Long dispatchId, String dispatchNumber, String reason) {
    }
}
