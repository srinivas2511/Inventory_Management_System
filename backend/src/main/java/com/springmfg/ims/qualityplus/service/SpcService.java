package com.springmfg.ims.qualityplus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.insight.alert.AlertService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.qualityplus.domain.SpcChartType;
import com.springmfg.ims.qualityplus.domain.SpcControlChart;
import com.springmfg.ims.qualityplus.domain.SpcDataPoint;
import com.springmfg.ims.qualityplus.dto.SpcDtos.AddPointsRequest;
import com.springmfg.ims.qualityplus.dto.SpcDtos.CapabilityDto;
import com.springmfg.ims.qualityplus.dto.SpcDtos.ChartDetailDto;
import com.springmfg.ims.qualityplus.dto.SpcDtos.ChartSummaryDto;
import com.springmfg.ims.qualityplus.dto.SpcDtos.PointDto;
import com.springmfg.ims.qualityplus.dto.SpcDtos.SaveChartRequest;
import com.springmfg.ims.qualityplus.dto.SpcDtos.UpdateChartRequest;
import com.springmfg.ims.qualityplus.repository.SpcControlChartRepository;
import com.springmfg.ims.qualityplus.repository.SpcDataPointRepository;

/**
 * Statistical process control (QMS-007, QMS-008). Limits are established once, automatically, when a chart has
 * collected enough subgroups ({@code spc.min_subgroups_for_limits}), and stay fixed until a manager recalculates
 * them; every later point is judged against them (Nelson rules 1 and 2, plus the range chart limit) and a violation
 * raises an SPC_OUT_OF_CONTROL alert.
 */
@Service
public class SpcService {

    static final String ALERT_TYPE = "SPC_OUT_OF_CONTROL";
    private static final int DETAIL_POINTS = 150;
    private static final String RANGE_RULE = "RANGE_R1";

    private final SpcControlChartRepository chartRepo;
    private final SpcDataPointRepository pointRepo;
    private final ProductRepository productRepo;
    private final SystemSettingService settings;
    private final AlertService alertService;
    private final AuditService auditService;

    public SpcService(SpcControlChartRepository chartRepo, SpcDataPointRepository pointRepo,
                      ProductRepository productRepo, SystemSettingService settings, AlertService alertService,
                      AuditService auditService) {
        this.chartRepo = chartRepo;
        this.pointRepo = pointRepo;
        this.productRepo = productRepo;
        this.settings = settings;
        this.alertService = alertService;
        this.auditService = auditService;
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChartSummaryDto> list(Long productId, String q) {
        List<SpcControlChart> charts = productId == null
                ? chartRepo.findAllByOrderByIdDesc() : chartRepo.findByProductIdOrderByIdDesc(productId);
        Map<Long, Product> products = productsOf(charts);
        String needle = q == null ? "" : q.trim().toLowerCase();
        return charts.stream()
                .filter(c -> needle.isEmpty() || c.getParameterName().toLowerCase().contains(needle)
                        || label(products.get(c.getProductId())).toLowerCase().contains(needle))
                .map(c -> summary(c, products.get(c.getProductId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChartDetailDto get(Long id) {
        return detail(load(id));
    }

    @Transactional(readOnly = true)
    public CapabilityDto capability(Long id, LocalDate from, LocalDate to) {
        SpcControlChart chart = load(id);
        if (chart.getChartType() != SpcChartType.XBAR_R) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Capability applies to measured (X-bar/R) charts, not " + chart.getChartType());
        }
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z")
                : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
        List<BigDecimal> values = pointRepo.findInPeriod(id, start, end).stream()
                .map(SpcDataPoint::getMeasuredValue).toList();
        SpcMath.Capability c = SpcMath.capability(values, chart.getUsl(), chart.getLsl());
        return new CapabilityDto(id, from, to, c.cp(), c.cpk(), c.cpu(), c.cpl(), c.mean(), c.stdDev(), c.count());
    }

    /** The out-of-control point behind a CAPA raised from SPC; checked so a CAPA cannot cite a healthy point. */
    @Transactional(readOnly = true)
    public SpcDataPoint requireOutOfControlPoint(Long pointId) {
        SpcDataPoint p = pointRepo.findById(pointId)
                .orElseThrow(() -> new NotFoundException("SPC data point not found: " + pointId));
        if (!p.isOutOfControl()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "SPC data point " + pointId + " is not out of control");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public String describePoint(SpcDataPoint p) {
        SpcControlChart chart = load(p.getControlChartId());
        Product product = productRepo.findById(chart.getProductId()).orElse(null);
        return label(product) + " / " + chart.getParameterName() + " subgroup " + p.getSubgroupNo() + " ("
                + p.getViolationRule() + ")";
    }

    // ---- maintenance -----------------------------------------------------------------------------------------

    @Transactional
    public ChartDetailDto create(SaveChartRequest req) {
        Product product = productRepo.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));
        String parameter = req.parameterName().trim();
        if (chartRepo.findByProductIdAndParameterNameAndActiveTrue(product.getId(), parameter).isPresent()) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "An active control chart already exists for " + label(product) + " / " + parameter);
        }
        int size = req.chartType() == SpcChartType.XBAR_R
                ? (req.subgroupSize() == null ? 5 : req.subgroupSize()) : 1;
        if (req.chartType() == SpcChartType.XBAR_R && !SpcMath.supportsSubgroupSize(size)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "X-bar/R charts need a subgroup size of 2 to 10");
        }
        requireSpecOrder(req.usl(), req.lsl());
        SpcControlChart chart = new SpcControlChart();
        chart.setProductId(product.getId());
        chart.setParameterName(parameter);
        chart.setChartType(req.chartType());
        chart.setSubgroupSize(size);
        chart.setUsl(SpcMath.round(req.usl()));
        chart.setLsl(SpcMath.round(req.lsl()));
        chart = chartRepo.save(chart);
        auditService.record("SPC_CHART_CREATE", "SpcControlChart", String.valueOf(chart.getId()), null,
                label(product) + " / " + parameter + " " + chart.getChartType(), null);
        return detail(chart);
    }

    @Transactional
    public ChartDetailDto update(Long id, UpdateChartRequest req) {
        SpcControlChart chart = load(id);
        requireSpecOrder(req.usl(), req.lsl());
        String before = "USL=" + chart.getUsl() + " LSL=" + chart.getLsl() + " active=" + chart.isActive();
        chart.setUsl(SpcMath.round(req.usl()));
        chart.setLsl(SpcMath.round(req.lsl()));
        if (req.active() != null) {
            if (req.active() && !chart.isActive()
                    && chartRepo.findByProductIdAndParameterNameAndActiveTrue(chart.getProductId(),
                            chart.getParameterName()).isPresent()) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                        "Another active chart already exists for this product and parameter");
            }
            chart.setActive(req.active());
        }
        chart = chartRepo.save(chart);
        auditService.record("SPC_CHART_UPDATE", "SpcControlChart", String.valueOf(id), before,
                "USL=" + chart.getUsl() + " LSL=" + chart.getLsl() + " active=" + chart.isActive(), null);
        return detail(chart);
    }

    // ---- data entry --------------------------------------------------------------------------------------------

    @Transactional
    public ChartDetailDto addPoints(Long chartId, AddPointsRequest req) {
        SpcControlChart chart = lockActive(chartId);
        Instant at = req.measuredAt() == null ? Instant.now() : req.measuredAt();
        switch (chart.getChartType()) {
            case XBAR_R -> {
                if (req.values() == null || req.values().isEmpty()) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "An X-bar/R chart needs at least one measured value");
                }
                for (BigDecimal v : req.values()) {
                    if (v == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A measured value is missing");
                    }
                    appendMeasurement(chart, v, null, at);
                }
            }
            case P -> {
                if (req.sampleSize() == null || req.sampleSize() <= 0 || req.defectCount() == null
                        || req.defectCount() < 0 || req.defectCount() > req.sampleSize()) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "A P chart point needs a sample size above zero and 0..sampleSize defectives");
                }
                appendAttribute(chart, req.sampleSize(), req.defectCount(), null, at);
            }
            case C -> {
                if (req.defectCount() == null || req.defectCount() < 0) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "A C chart point needs a defect count of zero or more");
                }
                appendAttribute(chart, null, req.defectCount(), null, at);
            }
        }
        return detail(chart);
    }

    /**
     * Records one inspection result on a chart. Idempotent per (chart, inspection): resubmitting results on an open
     * inspection never plots the same observation twice. Returns whether a point was added. Runs in its own
     * transaction because it is called after the inspection's transaction has committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFromInspection(Long chartId, Long inspectionId, BigDecimal value, Integer sampleSize,
                                        Integer defects, Instant at) {
        SpcControlChart chart = chartRepo.findByIdForUpdate(chartId)
                .orElseThrow(() -> new NotFoundException("SPC chart not found: " + chartId));
        if (!chart.isActive() || pointRepo.existsByControlChartIdAndInspectionId(chartId, inspectionId)) {
            return false;
        }
        switch (chart.getChartType()) {
            case XBAR_R -> appendMeasurement(chart, value, inspectionId, at);
            case P, C -> appendAttribute(chart, sampleSize, defects, inspectionId, at);
        }
        return true;
    }

    @Transactional
    public ChartDetailDto recalculateLimits(Long chartId) {
        SpcControlChart chart = lockActive(chartId);
        List<SpcDataPoint> points = pointRepo.findByControlChartIdOrderBySubgroupNoAscMeasurementNoAsc(chartId);
        if (plottedCount(chart, points) < 2) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "At least two plotted points are needed to calculate control limits");
        }
        String before = "CL=" + chart.getCl() + " UCL=" + chart.getUcl() + " LCL=" + chart.getLcl();
        establishLimits(chart, points);
        List<SpcDataPoint> plotted = plotted(chart, points);
        for (SpcDataPoint p : plotted) {
            p.setOutOfControl(false);
            p.setViolationRule(null);
        }
        for (SpcDataPoint p : plotted) {
            judge(chart, points, p, false);
        }
        pointRepo.saveAll(points);
        chartRepo.save(chart);
        auditService.record("SPC_LIMITS_RECALCULATED", "SpcControlChart", String.valueOf(chartId), before,
                "CL=" + chart.getCl() + " UCL=" + chart.getUcl() + " LCL=" + chart.getLcl(), null);
        return detail(chart);
    }

    // ---- plotting ------------------------------------------------------------------------------------------------

    private void appendMeasurement(SpcControlChart chart, BigDecimal value, Long inspectionId, Instant at) {
        int size = chart.getSubgroupSize();
        var last = pointRepo.findTopByControlChartIdOrderBySubgroupNoDescMeasurementNoDesc(chart.getId());
        int subgroupNo = 1;
        int measurementNo = 1;
        if (last.isPresent()) {
            if (last.get().getMeasurementNo() < size) {
                subgroupNo = last.get().getSubgroupNo();
                measurementNo = last.get().getMeasurementNo() + 1;
            } else {
                subgroupNo = last.get().getSubgroupNo() + 1;
            }
        }
        SpcDataPoint p = newPoint(chart, inspectionId, subgroupNo, measurementNo, SpcMath.round(value), at);
        pointRepo.save(p);
        if (measurementNo == size) {
            List<SpcDataPoint> all = pointRepo.findByControlChartIdOrderBySubgroupNoAscMeasurementNoAsc(chart.getId());
            List<BigDecimal> values = new ArrayList<>();
            final int sg = subgroupNo;
            List<SpcDataPoint> members = all.stream().filter(x -> x.getSubgroupNo() == sg).toList();
            members.forEach(m -> values.add(m.getMeasuredValue()));
            BigDecimal mean = SpcMath.round(SpcMath.mean(values));
            BigDecimal range = SpcMath.round(SpcMath.range(values));
            members.forEach(m -> {
                m.setSubgroupMean(mean);
                m.setSubgroupRange(range);
            });
            pointRepo.saveAll(members);
            afterPlot(chart, all, members.get(members.size() - 1));
        }
    }

    private void appendAttribute(SpcControlChart chart, Integer sampleSize, int defects, Long inspectionId,
                                 Instant at) {
        var last = pointRepo.findTopByControlChartIdOrderBySubgroupNoDescMeasurementNoDesc(chart.getId());
        int subgroupNo = last.map(l -> l.getSubgroupNo() + 1).orElse(1);
        BigDecimal value = chart.getChartType() == SpcChartType.P
                ? BigDecimal.valueOf(defects).divide(BigDecimal.valueOf(sampleSize), SpcMath.SCALE, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(defects).setScale(SpcMath.SCALE, RoundingMode.HALF_UP);
        SpcDataPoint p = newPoint(chart, inspectionId, subgroupNo, 1, value, at);
        p.setSampleSize(sampleSize);
        p.setDefectCount(defects);
        p = pointRepo.save(p);
        afterPlot(chart, pointRepo.findByControlChartIdOrderBySubgroupNoAscMeasurementNoAsc(chart.getId()), p);
    }

    private static SpcDataPoint newPoint(SpcControlChart chart, Long inspectionId, int subgroupNo, int measurementNo,
                                         BigDecimal value, Instant at) {
        SpcDataPoint p = new SpcDataPoint();
        p.setControlChartId(chart.getId());
        p.setInspectionId(inspectionId);
        p.setSubgroupNo(subgroupNo);
        p.setMeasurementNo(measurementNo);
        p.setMeasuredValue(value);
        p.setMeasuredAt(at);
        return p;
    }

    /** Establishes limits once enough points exist, then judges the newest plotted point. */
    private void afterPlot(SpcControlChart chart, List<SpcDataPoint> all, SpcDataPoint newest) {
        if (chart.getLimitsEstablishedAt() == null) {
            if (plottedCount(chart, all) < settings.getInt("spc.min_subgroups_for_limits", 20)) {
                return;
            }
            establishLimits(chart, all);
            chartRepo.save(chart);
        }
        judge(chart, all, newest, true);
        pointRepo.saveAll(all);
    }

    private void establishLimits(SpcControlChart chart, List<SpcDataPoint> all) {
        List<SpcDataPoint> plotted = plotted(chart, all);
        switch (chart.getChartType()) {
            case XBAR_R -> {
                SpcMath.XbarRLimits l = SpcMath.xbarR(
                        plotted.stream().map(SpcDataPoint::getSubgroupMean).toList(),
                        plotted.stream().map(SpcDataPoint::getSubgroupRange).toList(), chart.getSubgroupSize());
                chart.setCl(l.mean().cl());
                chart.setUcl(l.mean().ucl());
                chart.setLcl(l.mean().lcl());
                chart.setClR(l.range().cl());
                chart.setUclR(l.range().ucl());
                chart.setLclR(l.range().lcl());
            }
            case P -> {
                long defects = plotted.stream().mapToLong(SpcDataPoint::getDefectCount).sum();
                long sampled = plotted.stream().mapToLong(SpcDataPoint::getSampleSize).sum();
                BigDecimal average = BigDecimal.valueOf(sampled)
                        .divide(BigDecimal.valueOf(plotted.size()), SpcMath.SCALE, RoundingMode.HALF_UP);
                SpcMath.Limits l = SpcMath.pChart(defects, sampled, average);
                chart.setCl(l.cl());
                chart.setUcl(l.ucl());
                chart.setLcl(l.lcl());
            }
            case C -> {
                SpcMath.Limits l = SpcMath.cChart(plotted.stream().map(SpcDataPoint::getMeasuredValue).toList());
                chart.setCl(l.cl());
                chart.setUcl(l.ucl());
                chart.setLcl(l.lcl());
            }
        }
        chart.setLimitsEstablishedAt(Instant.now());
    }

    /** Applies the rules to one plotted point, using only the points up to and including it. */
    private void judge(SpcControlChart chart, List<SpcDataPoint> all, SpcDataPoint point, boolean alert) {
        List<SpcDataPoint> plotted = plotted(chart, all);
        int upTo = plotted.indexOf(point);
        if (upTo < 0) {
            return;
        }
        List<BigDecimal> history = new ArrayList<>();
        for (SpcDataPoint p : plotted.subList(0, upTo + 1)) {
            history.add(chart.getChartType() == SpcChartType.XBAR_R ? p.getSubgroupMean() : p.getMeasuredValue());
        }
        BigDecimal ucl = chart.getUcl();
        BigDecimal lcl = chart.getLcl();
        if (chart.getChartType() == SpcChartType.P) {
            SpcMath.Limits l = SpcMath.pLimitsFor(chart.getCl(), BigDecimal.valueOf(point.getSampleSize()));
            ucl = l.ucl();
            lcl = l.lcl();
            point.setPointUcl(ucl);
            point.setPointLcl(lcl);
        }
        List<String> rules = new ArrayList<>();
        String nelson = NelsonRules.evaluate(history, chart.getCl(), ucl, lcl,
                settings.getInt("spc.nelson_rule2_run_length", 9));
        if (nelson != null) {
            rules.add(nelson);
        }
        if (chart.getChartType() == SpcChartType.XBAR_R && point.getSubgroupRange() != null
                && ((chart.getUclR() != null && point.getSubgroupRange().compareTo(chart.getUclR()) > 0)
                || (chart.getLclR() != null && chart.getLclR().signum() > 0
                        && point.getSubgroupRange().compareTo(chart.getLclR()) < 0))) {
            rules.add(RANGE_RULE);
        }
        point.setOutOfControl(!rules.isEmpty());
        point.setViolationRule(rules.isEmpty() ? null : String.join(",", rules));
        if (alert && point.isOutOfControl()) {
            Product product = productRepo.findById(chart.getProductId()).orElse(null);
            alertService.raise(ALERT_TYPE, "HIGH", "SPC_CHART", chart.getId(),
                    "SPC out of control: " + label(product) + " / " + chart.getParameterName() + ", subgroup "
                            + point.getSubgroupNo() + " (" + point.getViolationRule() + ")");
        }
    }

    /** Points that appear on the chart: completed subgroups (X-bar/R) or every observation (P, C). */
    private static List<SpcDataPoint> plotted(SpcControlChart chart, List<SpcDataPoint> all) {
        if (chart.getChartType() != SpcChartType.XBAR_R) {
            return all;
        }
        return all.stream()
                .filter(p -> p.getMeasurementNo() == chart.getSubgroupSize() && p.getSubgroupMean() != null)
                .toList();
    }

    private static int plottedCount(SpcControlChart chart, List<SpcDataPoint> all) {
        return plotted(chart, all).size();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private SpcControlChart load(Long id) {
        return chartRepo.findById(id).orElseThrow(() -> new NotFoundException("SPC chart not found: " + id));
    }

    private SpcControlChart lockActive(Long id) {
        SpcControlChart chart = chartRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("SPC chart not found: " + id));
        if (!chart.isActive()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "This control chart is inactive");
        }
        return chart;
    }

    private static void requireSpecOrder(BigDecimal usl, BigDecimal lsl) {
        if (usl != null && lsl != null && usl.compareTo(lsl) <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The upper specification limit must be above the lower one");
        }
    }

    private Map<Long, Product> productsOf(List<SpcControlChart> charts) {
        Set<Long> ids = charts.stream().map(SpcControlChart::getProductId).collect(Collectors.toSet());
        Map<Long, Product> map = new HashMap<>();
        productRepo.findAllById(ids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private static String label(Product p) {
        return p == null ? "?" : p.getProductCode() + " " + p.getName();
    }

    private ChartSummaryDto summary(SpcControlChart c, Product p) {
        return new ChartSummaryDto(c.getId(), c.getProductId(), p == null ? null : p.getProductCode(),
                p == null ? null : p.getName(), c.getParameterName(), c.getChartType(), c.getSubgroupSize(),
                c.getLimitsEstablishedAt() != null, c.isActive(), pointRepo.countByControlChartId(c.getId()),
                pointRepo.countByControlChartIdAndOutOfControlTrue(c.getId()));
    }

    private ChartDetailDto detail(SpcControlChart c) {
        Product product = productRepo.findById(c.getProductId()).orElse(null);
        List<SpcDataPoint> all = pointRepo.findByControlChartIdOrderBySubgroupNoAscMeasurementNoAsc(c.getId());
        List<SpcDataPoint> tail = all.size() <= DETAIL_POINTS ? all : all.subList(all.size() - DETAIL_POINTS, all.size());
        return new ChartDetailDto(summary(c, product), c.getCl(), c.getUcl(), c.getLcl(), c.getClR(), c.getUclR(),
                c.getLclR(), c.getUsl(), c.getLsl(), c.getLimitsEstablishedAt(),
                tail.stream().map(SpcService::toDto).toList());
    }

    private static PointDto toDto(SpcDataPoint p) {
        return new PointDto(p.getId(), p.getInspectionId(), p.getSubgroupNo(), p.getMeasurementNo(),
                p.getMeasuredValue(), p.getMeasuredAt(), p.getSubgroupMean(), p.getSubgroupRange(),
                p.getSampleSize(), p.getDefectCount(), p.getPointUcl(), p.getPointLcl(), p.isOutOfControl(),
                p.getViolationRule());
    }
}
