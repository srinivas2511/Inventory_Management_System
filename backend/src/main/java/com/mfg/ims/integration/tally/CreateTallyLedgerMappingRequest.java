package com.mfg.ims.integration.tally;

public record CreateTallyLedgerMappingRequest(String glAccountCode, String tallyLedger, String notes) {}
