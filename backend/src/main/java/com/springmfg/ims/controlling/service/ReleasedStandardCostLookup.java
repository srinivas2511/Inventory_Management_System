package com.springmfg.ims.controlling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.controlling.repository.ProductCostEstimateRepository;
import com.springmfg.ims.production.service.StandardCostLookup;

/** Per-unit standard cost of the product's RELEASED cost estimate, at the ledger's four-decimal precision. */
@Component
class ReleasedStandardCostLookup implements StandardCostLookup {

    private final ProductCostEstimateRepository estimateRepo;

    ReleasedStandardCostLookup(ProductCostEstimateRepository estimateRepo) {
        this.estimateRepo = estimateRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> releasedUnitCost(Long productId) {
        return estimateRepo.findByProductIdAndStatus(productId, "RELEASED")
                .filter(e -> e.getCostLotSize() != null && e.getCostLotSize().signum() > 0)
                .map(e -> e.getTotalStandardCost().divide(e.getCostLotSize(), 4, RoundingMode.HALF_UP))
                .filter(c -> c.signum() > 0);
    }
}
