package com.mfg.ims.production.costrollup;

import com.mfg.ims.engineering.bom.BomItemRepository;
import com.mfg.ims.engineering.bom.BomRepository;
import com.mfg.ims.material.repository.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CostRollupService {

    private final CostRollupLogRepository logRepo;
    private final MaterialRepository materialRepo;
    private final BomRepository bomRepo;
    private final BomItemRepository bomItemRepo;

    public CostRollupService(CostRollupLogRepository logRepo, MaterialRepository materialRepo,
                             BomRepository bomRepo, BomItemRepository bomItemRepo) {
        this.logRepo = logRepo;
        this.materialRepo = materialRepo;
        this.bomRepo = bomRepo;
        this.bomItemRepo = bomItemRepo;
    }

    public List<CostRollupLogDto> runRollup(Long actorId) {
        Instant now = Instant.now();
        List<CostRollupLog> results = new ArrayList<>();

        materialRepo.findAll().forEach(mat -> {
            BigDecimal rolledCost = mat.getStandardCost() != null ? mat.getStandardCost() : BigDecimal.ZERO;

            CostRollupLog entry = new CostRollupLog();
            entry.setMaterialId(mat.getId());
            entry.setRolledCost(rolledCost);
            entry.setDepth(0);
            entry.setRolledAt(now);
            entry.setRolledBy(actorId);
            results.add(logRepo.save(entry));
        });

        return results.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CostRollupLogDto> list() {
        return logRepo.findByCompanyIdOrderByRolledAtDesc(1L).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    private CostRollupLogDto toDto(CostRollupLog log) {
        return new CostRollupLogDto(log.getId(), log.getMaterialId(), log.getRolledCost(),
                log.getBomId(), log.getDepth(), log.getRolledAt());
    }
}
