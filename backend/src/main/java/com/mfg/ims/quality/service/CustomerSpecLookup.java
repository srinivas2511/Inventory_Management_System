package com.mfg.ims.quality.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * SAL-006 / GC-5 port: quality asks "does the customer of this sales order have its own limits for this product's
 * inspection parameter?" and the sales module answers. The port keeps the dependency pointing from sales to quality,
 * as for every other sales-quality link.
 */
public interface CustomerSpecLookup {

    /** The customer's own limits that replace the standard ones, with the spec reference they came from. */
    record Limits(BigDecimal lower, BigDecimal upper, String specReference) {}

    Optional<Limits> find(Long salesOrderId, Long productId, String parameterName);
}
