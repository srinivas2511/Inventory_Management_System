package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.production.domain.MaterialIssue;
import com.springmfg.ims.production.domain.MaterialIssueLine;
import com.springmfg.ims.production.domain.ProductionOrderMaterial;
import com.springmfg.ims.production.domain.ProductionOutput;
import com.springmfg.ims.production.repository.MaterialIssueLineRepository;
import com.springmfg.ims.production.repository.MaterialIssueRepository;
import com.springmfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.springmfg.ims.production.repository.ProductionOutputRepository;

/**
 * Decides the unit cost at which a production order's good output enters finished-goods stock (DESIGN 4 and 16).
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

    private final MaterialIssueRepository issueRepo;
    private final MaterialIssueLineRepository issueLineRepo;
    private final ProductionOrderMaterialRepository orderMaterialRepo;
    private final ProductionOutputRepository outputRepo;
    private final StockLocationQueryService stockQuery;
    private final List<StandardCostLookup> standardCostLookups;

    public FinishedGoodsCostService(MaterialIssueRepository issueRepo,
                                    MaterialIssueLineRepository issueLineRepo,
                                    ProductionOrderMaterialRepository orderMaterialRepo,
                                    ProductionOutputRepository outputRepo,
                                    StockLocationQueryService stockQuery,
                                    List<StandardCostLookup> standardCostLookups) {
        this.issueRepo = issueRepo;
        this.issueLineRepo = issueLineRepo;
        this.orderMaterialRepo = orderMaterialRepo;
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

    /** (material cost still in WIP after scrap) / good quantity, at ledger precision; never negative. */
    static BigDecimal actualUnitCost(BigDecimal materialCost, BigDecimal scrapRelief, BigDecimal goodQty) {
        BigDecimal inWip = materialCost.subtract(scrapRelief);
        if (inWip.signum() <= 0 || goodQty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return inWip.divide(goodQty, 4, RoundingMode.HALF_UP);
    }

    /** Issued value net of returns, per material at its average issue cost. */
    private BigDecimal materialCost(Long productionOrderId) {
        Map<Long, BigDecimal[]> issued = new HashMap<>(); // materialId -> [qty, value]
        for (MaterialIssue issue : issueRepo.findByProductionOrderId(productionOrderId)) {
            for (MaterialIssueLine line : issueLineRepo.findByMaterialIssueId(issue.getId())) {
                BigDecimal[] acc = issued.computeIfAbsent(line.getMaterialId(),
                        k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
                acc[0] = acc[0].add(line.getQuantity());
                acc[1] = acc[1].add(line.getQuantity().multiply(line.getUnitCost()));
            }
        }
        Map<Long, BigDecimal> returned = new HashMap<>();
        for (ProductionOrderMaterial pom : orderMaterialRepo.findByProductionOrderId(productionOrderId)) {
            returned.put(pom.getMaterialId(), pom.getReturnedQty());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal[]> e : issued.entrySet()) {
            BigDecimal qty = e.getValue()[0];
            if (qty.signum() == 0) {
                continue;
            }
            BigDecimal avg = e.getValue()[1].divide(qty, 8, RoundingMode.HALF_UP);
            BigDecimal net = qty.subtract(returned.getOrDefault(e.getKey(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            total = total.add(net.multiply(avg));
        }
        return total.setScale(4, RoundingMode.HALF_UP);
    }
}
