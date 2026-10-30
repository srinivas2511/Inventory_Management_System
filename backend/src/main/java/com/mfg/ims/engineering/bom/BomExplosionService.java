package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;

/**
 * Multi-level BOM explosion (up to 10 levels) and cost check (13.1).
 * Circular references are detected via a visited-BOM-id set; exceeding depth throws a BusinessRuleException.
 */
@Service
public class BomExplosionService {

    static final int MAX_DEPTH = 10;

    private final BomRepository bomRepo;
    private final MaterialRepository materialRepo;

    public BomExplosionService(BomRepository bomRepo, MaterialRepository materialRepo) {
        this.bomRepo = bomRepo;
        this.materialRepo = materialRepo;
    }

    @Transactional(readOnly = true)
    public BomExplosionDto explode(Long bomId, BigDecimal qty) {
        Bom bom = bomRepo.findById(bomId)
                .orElseThrow(() -> new NotFoundException("BOM not found: " + bomId));
        List<BomExplosionLineDto> lines = new ArrayList<>();
        explodeRecursive(bomId, qty, 0, new HashSet<>(), lines);
        return new BomExplosionDto(bomId, bom.getBomNumber(), qty, lines);
    }

    private void explodeRecursive(Long bomId, BigDecimal parentQty, int depth, Set<Long> visited,
                                  List<BomExplosionLineDto> result) {
        if (depth > MAX_DEPTH) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "BOM explosion exceeds maximum depth of " + MAX_DEPTH);
        }
        if (!visited.add(bomId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Circular BOM reference detected at BOM ID " + bomId);
        }

        Bom bom = bomRepo.findById(bomId).orElseThrow(() -> new NotFoundException("BOM not found: " + bomId));
        for (BomItem item : bom.getItems()) {
            if (!"MATERIAL".equals(item.getItemKind())) continue;
            Long matId = item.getMaterialId();
            if (matId == null) continue;

            BigDecimal lineQty = item.getRequiredQty() != null ? item.getRequiredQty() : BigDecimal.ZERO;
            BigDecimal scrap = item.getScrapPct() != null ? item.getScrapPct() : BigDecimal.ZERO;
            BigDecimal factor = BigDecimal.ONE.add(scrap.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal totalQty = lineQty.multiply(parentQty).multiply(factor).setScale(6, RoundingMode.HALF_UP);

            Material mat = materialRepo.findById(matId).orElse(null);
            String matCode = mat != null ? mat.getMaterialCode() : null;
            String matName = mat != null ? mat.getName() : null;
            String uom = item.getUom();

            // Build alternates list from the alternate_material_id column
            List<BomExplosionLineDto.AlternateItem> alternates = new ArrayList<>();
            if (item.getAlternateMaterialId() != null) {
                Material alt = materialRepo.findById(item.getAlternateMaterialId()).orElse(null);
                if (alt != null) {
                    alternates.add(new BomExplosionLineDto.AlternateItem(
                            alt.getId(), alt.getMaterialCode(), alt.getName(),
                            item.getAlternateFactor() != null ? item.getAlternateFactor() : BigDecimal.ONE));
                }
            }

            result.add(new BomExplosionLineDto(depth, matId, matCode, matName, uom, totalQty, alternates));

            // If this material is also the product of a sub-assembly BOM (product_id = materialId), recurse
            Set<Long> visitedCopy = new HashSet<>(visited);
            bomRepo.findByProductIdAndStatus(matId, "ACTIVE").ifPresent(subBom ->
                    explodeRecursive(subBom.getId(), totalQty, depth + 1, visitedCopy, result));
        }

        visited.remove(bomId);
    }

    @Transactional(readOnly = true)
    public BomCostCheckDto costCheck(Long bomId) {
        Bom bom = bomRepo.findById(bomId)
                .orElseThrow(() -> new NotFoundException("BOM not found: " + bomId));

        List<BomCostCheckDto.CostLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (BomItem item : bom.getItems()) {
            if (!"MATERIAL".equals(item.getItemKind()) || item.getMaterialId() == null) continue;
            Material mat = materialRepo.findById(item.getMaterialId()).orElse(null);
            if (mat == null) continue;
            BigDecimal qty = item.getRequiredQty() != null ? item.getRequiredQty() : BigDecimal.ZERO;
            BigDecimal unitCost = mat.getStandardCost() != null ? mat.getStandardCost() : BigDecimal.ZERO;
            BigDecimal lineCost = qty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            lines.add(new BomCostCheckDto.CostLine(mat.getId(), mat.getName(), qty, unitCost, lineCost));
            total = total.add(lineCost);
        }

        return new BomCostCheckDto(bomId, total.setScale(4, RoundingMode.HALF_UP), lines);
    }
}
