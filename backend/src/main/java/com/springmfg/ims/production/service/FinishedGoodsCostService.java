package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.production.domain.MaterialIssueLine;
import com.springmfg.ims.production.domain.ProductionOutput;
import com.springmfg.ims.production.repository.MaterialIssueLineRepository;
import com.springmfg.ims.production.repository.ProductionOutputRepository;

/**
 * Decides the unit cost at which a production order's good output enters finished-goods stock (DESIGN 4 and 16).
 * The material in the cost is what the order's pieces consumed; issued material that was not consumed is returned or
 * written off when the order is closed and never reaches FG.
 *
 * <p>With a RELEASED cost estimate the output is received at standard cost, which is what the controlling variance
 * journal assumes: it relieves WIP by (actual - standard), so the receipt must relieve WIP by the standard. Without
 * one, the output is received at the actual material cost left in WIP after scrap, so WIP is relieved exactly once.
 * Conversion cost (labour, machine, overhead) is only ever part of a released standard; no rate is invented here.
 */
@Service
public class FinishedGoodsCostService {

    public enum Basis { STANDARD, ACTUAL_MATERIAL, NONE }

    /** The unit cost to post, how it was derived, and the figures behind it. */
    public record FgCost(BigDecimal unitCost, Basis basis, BigDecimal materialCost, BigDecimal scrapRelief) {
    }

    private final MaterialIssueLineRepository issueLineRepo;
    private final ProductionOutputRepository outputRepo;
    private final StockLocationQueryService stockQuery;
    private final List<StandardCostLookup> standardCostLookups;

    public FinishedGoodsCostService(MaterialIssueLineRepository issueLineRepo,
                                    ProductionOutputRepository outputRepo,
                                    StockLocationQueryService stockQuery,
                                    List<StandardCostLookup> standardCostLookups) {
        this.issueLineRepo = issueLineRepo;
        this.outputRepo = outputRepo;
        this.stockQuery = stockQuery;
        this.standardCostLookups = standardCostLookups;
    }

    @Transactional(readOnly = true)
    public FgCost resolve(Long productionOrderId, Long productId, BigDecimal goodQty) {
        BigDecimal material = materialCost(productionOrderId);
        List<Long> outputIds = outputRepo.findByProductionOrderId(productionOrderId).stream()
                .map(ProductionOutput::getId).toList();
        BigDecimal scrap = stockQuery.scrapValueOfOutputs(outputIds);
        if (goodQty == null || goodQty.signum() <= 0) {
            return new FgCost(BigDecimal.ZERO.setScale(4), Basis.NONE, material, scrap);
        }
        for (StandardCostLookup lookup : standardCostLookups) {
            var standard = lookup.releasedUnitCost(productId);
            if (standard.isPresent()) {
                return new FgCost(standard.get(), Basis.STANDARD, material, scrap);
            }
        }
        BigDecimal unit = actualUnitCost(material, scrap, goodQty);
        return new FgCost(unit, unit.signum() > 0 ? Basis.ACTUAL_MATERIAL : Basis.NONE, material, scrap);
    }

    /**
     * The value of the scrap recorded on the order's operations. It was charged to scrap loss (Dr 5202) and relieved from
     * WIP (Cr 1501) when it was recorded, so no later journal may charge it again.
     */
    @Transactional(readOnly = true)
    public BigDecimal scrapRelieved(Long productionOrderId) {
        List<Long> outputIds = outputRepo.findByProductionOrderId(productionOrderId).stream()
                .map(ProductionOutput::getId).toList();
        return stockQuery.scrapValueOfOutputs(outputIds);
    }

    /** (material cost still in WIP after scrap) / good quantity, at ledger precision; never negative. */
    static BigDecimal actualUnitCost(BigDecimal materialCost, BigDecimal scrapRelief, BigDecimal goodQty) {
        BigDecimal inWip = materialCost.subtract(scrapRelief);
        if (inWip.signum() <= 0 || goodQty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return inWip.divide(goodQty, 4, RoundingMode.HALF_UP);
    }

    /** What the order's pieces consumed: each line's consumed quantity at the price it was issued at. */
    private BigDecimal materialCost(Long productionOrderId) {
        BigDecimal total = BigDecimal.ZERO;
        for (MaterialIssueLine line : issueLineRepo.findByProductionOrder(productionOrderId)) {
            total = total.add(line.getConsumedQty().multiply(line.getUnitCost()));
        }
        return total.setScale(4, RoundingMode.HALF_UP);
    }
}
