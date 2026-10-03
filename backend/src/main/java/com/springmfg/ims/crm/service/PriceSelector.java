package com.springmfg.ims.crm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.springmfg.ims.crm.domain.CustomerPrice;

/**
 * Picks the winning price among the rows that already apply by date and quantity: a customer-specific row beats a
 * standard row, then the highest minimum quantity (best volume tier), then the most recent valid-from.
 */
final class PriceSelector {

    private PriceSelector() {}

    static Optional<CustomerPrice> select(List<CustomerPrice> applicable) {
        return applicable.stream().max(
                Comparator.comparing((CustomerPrice p) -> p.getCustomerId() != null)
                        .thenComparing(CustomerPrice::getMinQty)
                        .thenComparing(CustomerPrice::getValidFrom));
    }
}
