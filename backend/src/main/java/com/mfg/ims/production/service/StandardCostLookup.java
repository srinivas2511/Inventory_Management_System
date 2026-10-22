package com.mfg.ims.production.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Released standard cost per unit of a product. Production owns this port and controlling implements it, so
 * production never depends on the controlling module (see ControllingArchTest).
 */
public interface StandardCostLookup {

    Optional<BigDecimal> releasedUnitCost(Long productId);
}
