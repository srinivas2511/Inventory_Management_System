package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordChargesRequest(String chargeDescription, BigDecimal amount, LocalDate chargeDate) {}
