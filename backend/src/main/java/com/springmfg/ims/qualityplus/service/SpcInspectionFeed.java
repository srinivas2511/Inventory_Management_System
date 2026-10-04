package com.springmfg.ims.qualityplus.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.springmfg.ims.common.event.InspectionResultsSubmittedEvent;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.qualityplus.domain.SpcControlChart;
import com.springmfg.ims.qualityplus.repository.SpcControlChartRepository;

/**
 * Feeds inspection results into control charts (R16-4.2). It runs after the inspection has committed and never
 * fails it: an SPC problem is logged, not thrown. A chart is fed when its product and parameter match the inspection.
 * X-bar/R charts take the measured value; P and C charts take the inspection's rejected quantity as the defect count
 * (and accepted plus rejected as the sample size) when the inspection checked that parameter.
 */
@Component
public class SpcInspectionFeed {

    private static final Logger log = LoggerFactory.getLogger(SpcInspectionFeed.class);

    private final QualityInspectionRepository inspectionRepo;
    private final QualityInspectionItemRepository itemRepo;
    private final EntityManager em;
    private final ProductionOrderRepository orderRepo;
    private final SpcControlChartRepository chartRepo;
    private final SpcService spcService;

    public SpcInspectionFeed(QualityInspectionRepository inspectionRepo, QualityInspectionItemRepository itemRepo,
                             EntityManager em, ProductionOrderRepository orderRepo,
                             SpcControlChartRepository chartRepo, SpcService spcService) {
        this.inspectionRepo = inspectionRepo;
        this.itemRepo = itemRepo;
        this.em = em;
        this.orderRepo = orderRepo;
        this.chartRepo = chartRepo;
        this.spcService = spcService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResultsSubmitted(InspectionResultsSubmittedEvent event) {
        try {
            feed(event.inspectionId());
        } catch (RuntimeException e) {
            log.error("SPC feed failed for inspection {}: {}", event.inspectionId(), e.getMessage(), e);
        }
    }

    void feed(Long inspectionId) {
        QualityInspection inspection = inspectionRepo.findById(inspectionId).orElse(null);
        if (inspection == null) {
            return;
        }
        Long productId = productOf(inspection);
        if (productId == null) {
            return;
        }
        List<SpcControlChart> charts = chartRepo.findByProductIdAndActiveTrue(productId);
        if (charts.isEmpty()) {
            return;
        }
        List<QualityInspectionItem> items = itemRepo.findByInspectionId(inspectionId);
        Instant at = inspection.getInspectedAt() == null ? Instant.now() : inspection.getInspectedAt();
        for (SpcControlChart chart : charts) {
            QualityInspectionItem item = items.stream()
                    .filter(i -> chart.getParameterName().equalsIgnoreCase(i.getParameterName())).findFirst()
                    .orElse(null);
            if (item == null) {
                continue;
            }
            try {
                feedChart(chart, inspection, item, at);
            } catch (RuntimeException e) {
                log.error("SPC feed failed for chart {} on inspection {}: {}", chart.getId(), inspectionId,
                        e.getMessage(), e);
            }
        }
    }

    private void feedChart(SpcControlChart chart, QualityInspection inspection, QualityInspectionItem item,
                           Instant at) {
        switch (chart.getChartType()) {
            case XBAR_R -> {
                if (item.getActualValue() != null) {
                    spcService.recordFromInspection(chart.getId(), inspection.getId(), item.getActualValue(), null,
                            null, at);
                }
            }
            case P, C -> {
                Integer defects = wholeNumber(inspection.getRejectedQty());
                Integer sampled = wholeNumber(inspection.getAcceptedQty().add(inspection.getRejectedQty()));
                if (defects != null && sampled != null && sampled > 0 && defects <= sampled) {
                    spcService.recordFromInspection(chart.getId(), inspection.getId(), null, sampled, defects, at);
                }
            }
        }
    }

    private Long productOf(QualityInspection inspection) {
        if (inspection.getFgBatchId() != null) {
            List<Long> ids = em.createQuery("SELECT b.product.id FROM FgBatch b WHERE b.id = :id", Long.class)
                    .setParameter("id", inspection.getFgBatchId()).getResultList();
            return ids.isEmpty() ? null : ids.get(0);
        }
        if (inspection.getProductionOrderId() != null) {
            return orderRepo.findById(inspection.getProductionOrderId()).map(ProductionOrder::getProductId)
                    .orElse(null);
        }
        return null;
    }

    private static Integer wholeNumber(BigDecimal v) {
        if (v == null || v.stripTrailingZeros().scale() > 0 || v.signum() < 0) {
            return null;
        }
        return v.intValueExact();
    }
}
