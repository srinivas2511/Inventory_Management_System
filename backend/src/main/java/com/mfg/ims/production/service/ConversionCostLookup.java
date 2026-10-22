package com.mfg.ims.production.service;

import java.math.BigDecimal;

/**
 * What a production order's operations cost to run (labour, machine, overhead). Production owns this port and
 * controlling implements it, so production never depends on the controlling module (see ControllingArchTest).
 */
public interface ConversionCostLookup {

    /**
     * Fixes the order's actual conversion cost in the caller's transaction and returns it (zero when no cost centre
     * rate applies). Calling it again returns the amount fixed the first time.
     */
    BigDecimal freezeConversionCost(Long productionOrderId);
}
