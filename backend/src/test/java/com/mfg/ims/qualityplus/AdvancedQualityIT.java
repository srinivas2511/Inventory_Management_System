package com.mfg.ims.qualityplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.insight.report.ReportRequest;
import com.mfg.ims.insight.report.definitions.QualityComplaintsReport;
import com.mfg.ims.inventory.domain.FgBatch;
import com.mfg.ims.inventory.repository.FgBatchRepository;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.plm.domain.EcoChangeCategory;
import com.mfg.ims.plm.domain.EcoItemType;
import com.mfg.ims.plm.domain.ProductLifecycleStatus;
import com.mfg.ims.plm.dto.EcoDtos.EcoDto;
import com.mfg.ims.plm.dto.EcoDtos.SaveEcoItemRequest;
import com.mfg.ims.plm.dto.EcoDtos.SaveEcoRequest;
import com.mfg.ims.plm.dto.LifecycleDtos.TransitionRequest;
import com.mfg.ims.plm.service.EcoService;
import com.mfg.ims.plm.service.ProductLifecycleService;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.quality.domain.InspectionPlan;
import com.mfg.ims.quality.domain.InspectionPlanParameter;
import com.mfg.ims.quality.domain.QualityInspection;
import com.mfg.ims.quality.domain.QualityInspectionItem;
import com.mfg.ims.quality.domain.Rejection;
import com.mfg.ims.quality.dto.InspectionResultRequest;
import com.mfg.ims.quality.dto.InspectionResultRequest.ItemResultRequest;
import com.mfg.ims.quality.repository.InspectionPlanRepository;
import com.mfg.ims.quality.repository.QualityInspectionItemRepository;
import com.mfg.ims.quality.repository.QualityInspectionRepository;
import com.mfg.ims.quality.repository.RejectionReasonRepository;
import com.mfg.ims.quality.repository.RejectionRepository;
import com.mfg.ims.quality.service.InspectionService;
import com.mfg.ims.qualityplus.domain.CapaSourceType;
import com.mfg.ims.qualityplus.domain.CapaStatus;
import com.mfg.ims.qualityplus.domain.ComplaintCategory;
import com.mfg.ims.qualityplus.domain.ComplaintDisposition;
import com.mfg.ims.qualityplus.domain.ComplaintStatus;
import com.mfg.ims.qualityplus.domain.FaiStatus;
import com.mfg.ims.qualityplus.domain.FaiTrigger;
import com.mfg.ims.qualityplus.domain.SpcChartType;
import com.mfg.ims.qualityplus.dto.CapaDtos.AdvanceCapaRequest;
import com.mfg.ims.qualityplus.dto.CapaDtos.CapaDto;
import com.mfg.ims.qualityplus.dto.CapaDtos.CloseCapaRequest;
import com.mfg.ims.qualityplus.dto.CapaDtos.SaveCapaRequest;
import com.mfg.ims.qualityplus.dto.ComplaintDtos.ComplaintDto;
import com.mfg.ims.qualityplus.dto.ComplaintDtos.SaveComplaintRequest;
import com.mfg.ims.qualityplus.dto.ComplaintDtos.TransitionComplaintRequest;
import com.mfg.ims.qualityplus.dto.FaiDtos.CompleteFaiRequest;
import com.mfg.ims.qualityplus.dto.FaiDtos.FaiDto;
import com.mfg.ims.qualityplus.dto.FaiDtos.FaiReportDto;
import com.mfg.ims.qualityplus.dto.FaiDtos.FaiResultValue;
import com.mfg.ims.qualityplus.dto.FaiDtos.StartFaiRequest;
import com.mfg.ims.qualityplus.dto.FaiDtos.SubmitFaiResultsRequest;
import com.mfg.ims.qualityplus.dto.SpcDtos.AddPointsRequest;
import com.mfg.ims.qualityplus.dto.SpcDtos.CapabilityDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.ChartDetailDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.PointDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.SaveChartRequest;
import com.mfg.ims.qualityplus.dto.SpcDtos.UpdateChartRequest;
import com.mfg.ims.qualityplus.repository.FirstArticleInspectionRepository;
import com.mfg.ims.qualityplus.repository.SpcDataPointRepository;
import com.mfg.ims.qualityplus.service.CapaService;
import com.mfg.ims.qualityplus.service.ComplaintService;
import com.mfg.ims.qualityplus.service.FaiService;
import com.mfg.ims.qualityplus.service.SpcService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Integration tests for R16 Advanced Quality (PLAN gate M16): SPC charts fed from inspection data, out-of-control
 * detection and its alert, CAPA from a rejection, complaints traced to an FG batch, and FAI triggered by an ECO.
 * Deliberately not {@code @Transactional}: the SPC feed runs after the inspection commits.
 */
class AdvancedQualityIT extends AbstractIntegrationTest {

    @Autowired SpcService spcService;
    @Autowired CapaService capaService;
    @Autowired ComplaintService complaintService;
    @Autowired FaiService faiService;
    @Autowired InspectionService inspectionService;
    @Autowired EcoService ecoService;
    @Autowired ProductLifecycleService lifecycleService;
    @Autowired SystemSettingService settings;
    @Autowired InspectionPlanRepository planRepo;
    @Autowired QualityInspectionRepository inspectionRepo;
    @Autowired QualityInspectionItemRepository itemRepo;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired RejectionRepository rejectionRepo;
    @Autowired RejectionReasonRepository reasonRepo;
    @Autowired SpcDataPointRepository pointRepo;
    @Autowired FirstArticleInspectionRepository faiRepo;
    @Autowired QualityComplaintsReport complaintsReport;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long userId;

    @BeforeEach
    void loginAndLowerTheLimitThreshold() {
        settings.update("spc.min_subgroups_for_limits", "5");
        userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'QMS Tester', ?, 'x') RETURNING id
                """, Long.class, td.code("qms"), td.code("qms") + "@example.com");
        String[] authorities = { "QUALITY_VIEW", "QUALITY_INSPECT", "QUALITY_APPROVE", "SPC_MANAGE", "CAPA_MANAGE",
                "CAPA_CLOSE", "COMPLAINT_MANAGE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void restore() {
        SecurityContextHolder.clearContext();
        settings.update("spc.min_subgroups_for_limits", "20");
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static List<BigDecimal> values(String... v) {
        return Arrays.stream(v).map(BigDecimal::new).toList();
    }

    private ChartDetailDto xbarChart(Product p, String parameter) {
        return spcService.create(new SaveChartRequest(p.getId(), parameter, SpcChartType.XBAR_R, 2,
                new BigDecimal("10.6"), new BigDecimal("9.4")));
    }

    private ChartDetailDto addSubgroup(Long chartId, String a, String b) {
        return spcService.addPoints(chartId, new AddPointsRequest(values(a, b), null, null, null));
    }

    /** Five in-control subgroups: grand mean 10.0, R-bar 0.16, so UCL 10.3008 / LCL 9.6992 / UCL_R 0.52272. */
    private void establishLimits(Long chartId) {
        addSubgroup(chartId, "10.0", "10.2");
        addSubgroup(chartId, "9.9", "10.1");
        addSubgroup(chartId, "10.0", "10.0");
        addSubgroup(chartId, "10.1", "9.9");
        addSubgroup(chartId, "9.8", "10.0");
    }

    private int alertCount(Long chartId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ims.alerts WHERE alert_type = 'SPC_OUT_OF_CONTROL' "
                + "AND entity_type = 'SPC_CHART' AND entity_id = ?", Integer.class, chartId);
    }

    private int auditCount(String action, String entity, Object entityId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ims.audit_logs WHERE action = ? AND entity = ? AND entity_id = ?",
                Integer.class, action, entity, String.valueOf(entityId));
    }

    private FgBatch fgBatch(Product p) {
        FgBatch b = new FgBatch();
        b.setBatchNumber(td.code("FG"));
        b.setProduct(p);
        b.setProductionOrderId(999L);
        b.setProducedQty(new BigDecimal("100.000"));
        b.setProducedOn(LocalDate.now());
        b.setQualityStatus("QUALITY_PENDING");
        b.setUnitCost(new BigDecimal("50.0000"));
        return fgBatchRepo.save(b);
    }

    /** A FINAL inspection of the batch with one measured item per parameter, results not yet submitted. */
    private QualityInspection finalInspection(FgBatch batch, String... parameters) {
        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(td.code("QI"));
        insp.setInspectionType("FINAL");
        insp.setStatus("PENDING");
        insp.setFgBatchId(batch.getId());
        insp.setProductionOrderId(batch.getProductionOrderId());
        insp.setInspectedQty(new BigDecimal("10"));
        insp = inspectionRepo.save(insp);
        int seq = 1;
        for (String parameter : parameters) {
            QualityInspectionItem item = new QualityInspectionItem();
            item.setInspection(insp);
            item.setSeq(seq++);
            item.setParameterName(parameter);
            item.setLowerLimit(new BigDecimal("0"));
            item.setUpperLimit(new BigDecimal("100"));
            item.setMandatory(true);
            itemRepo.save(item);
        }
        return insp;
    }

    private void submit(QualityInspection insp, String accepted, String rejected, String... itemValues) {
        List<QualityInspectionItem> items = itemRepo.findByInspectionId(insp.getId());
        List<ItemResultRequest> results = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            results.add(new ItemResultRequest(items.get(i).getId(),
                    i < itemValues.length ? new BigDecimal(itemValues[i]) : null, null, null));
        }
        inspectionService.submitResult(insp.getId(),
                new InspectionResultRequest(new BigDecimal(accepted), new BigDecimal(rejected), null, results));
    }

    // ---- SPC: limits and out-of-control detection -----------------------------------------------------------------

    @Test
    void limits_are_established_after_the_minimum_subgroups_and_a_point_beyond_ucl_raises_an_alert() {
        Product p = td.product();
        ChartDetailDto chart = xbarChart(p, "Wire diameter");
        Long id = chart.summary().id();
        assertThat(chart.ucl()).isNull();

        addSubgroup(id, "10.0", "10.2");
        addSubgroup(id, "9.9", "10.1");
        addSubgroup(id, "10.0", "10.0");
        addSubgroup(id, "10.1", "9.9");
        assertThat(spcService.get(id).ucl()).as("four subgroups are not enough").isNull();
        ChartDetailDto established = addSubgroup(id, "9.8", "10.0");

        assertThat(established.cl()).isEqualByComparingTo("10.000000");
        assertThat(established.ucl()).isEqualByComparingTo("10.300800");
        assertThat(established.lcl()).isEqualByComparingTo("9.699200");
        assertThat(established.uclR()).isEqualByComparingTo("0.522720");
        assertThat(established.lclR()).isEqualByComparingTo("0");
        assertThat(established.limitsEstablishedAt()).isNotNull();
        assertThat(established.summary().limitsEstablished()).isTrue();
        assertThat(established.summary().outOfControlCount()).isZero();
        assertThat(alertCount(id)).isZero();

        // mean 10.55 is above UCL 10.3008 (Nelson rule 1); the range 0.1 is fine
        ChartDetailDto flagged = addSubgroup(id, "10.5", "10.6");
        PointDto last = flagged.points().get(flagged.points().size() - 1);
        assertThat(last.outOfControl()).isTrue();
        assertThat(last.violationRule()).isEqualTo("NELSON_R1");
        assertThat(last.subgroupMean()).isEqualByComparingTo("10.550000");
        assertThat(flagged.summary().outOfControlCount()).isEqualTo(1);
        assertThat(alertCount(id)).as("an out-of-control signal raises one alert").isEqualTo(1);

        // mean 9.5 is below LCL and the range 1.0 is above UCL_R: both limits fire
        ChartDetailDto both = addSubgroup(id, "9.0", "10.0");
        PointDto second = both.points().get(both.points().size() - 1);
        assertThat(second.violationRule()).isEqualTo("NELSON_R1,RANGE_R1");
        assertThat(alertCount(id)).as("the open alert is refreshed, not duplicated").isEqualTo(1);
    }

    @Test
    void nine_subgroups_above_the_centre_line_are_flagged_by_nelson_rule_2_at_the_ninth() {
        Product p = td.product();
        Long id = xbarChart(p, "Free length").summary().id();
        for (int i = 0; i < 5; i++) {
            addSubgroup(id, "9.9", "10.1");
        }
        ChartDetailDto detail = null;
        for (int i = 0; i < 9; i++) {
            detail = addSubgroup(id, "10.1", "10.2");
        }
        List<PointDto> flagged = detail.points().stream().filter(PointDto::outOfControl).toList();
        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).violationRule()).isEqualTo("NELSON_R2");
        assertThat(flagged.get(0).subgroupNo()).isEqualTo(14);
        assertThat(alertCount(id)).isEqualTo(1);
    }

    @Test
    void recalculating_limits_uses_all_data_audits_and_raises_no_new_alert() {
        Product p = td.product();
        Long id = xbarChart(p, "Coil diameter").summary().id();
        establishLimits(id);
        addSubgroup(id, "10.5", "10.6");
        assertThat(alertCount(id)).isEqualTo(1);

        ChartDetailDto recalculated = spcService.recalculateLimits(id);

        // grand mean 60.55/6 = 10.091667, R-bar 0.9/6 = 0.15, A2 = 1.88 for n = 2
        assertThat(recalculated.cl()).isEqualByComparingTo("10.091667");
        assertThat(recalculated.ucl()).isEqualByComparingTo("10.373667");
        assertThat(recalculated.lcl()).isEqualByComparingTo("9.809667");
        assertThat(auditCount("SPC_LIMITS_RECALCULATED", "SpcControlChart", id)).isEqualTo(1);
        assertThat(alertCount(id)).isEqualTo(1);
        assertThat(recalculated.summary().outOfControlCount()).as("10.55 is still beyond the new UCL").isEqualTo(1);

        Long fresh = xbarChart(td.product(), "Pitch").summary().id();
        addSubgroup(fresh, "10.0", "10.2");
        assertThatThrownBy(() -> spcService.recalculateLimits(fresh)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void p_and_c_charts_judge_each_observation_against_its_own_limits() {
        Product p = td.product();
        Long pChart = spcService.create(new SaveChartRequest(p.getId(), "Surface defective", SpcChartType.P, null,
                null, null)).summary().id();
        for (int defects : new int[] { 2, 3, 1, 4, 2 }) {
            spcService.addPoints(pChart, new AddPointsRequest(null, 100, defects, null));
        }
        ChartDetailDto established = spcService.get(pChart);
        assertThat(established.cl()).isEqualByComparingTo("0.024000");
        assertThat(established.ucl()).isGreaterThan(established.cl());

        ChartDetailDto flagged = spcService.addPoints(pChart, new AddPointsRequest(null, 100, 15, null));
        PointDto bad = flagged.points().get(flagged.points().size() - 1);
        assertThat(bad.measuredValue()).isEqualByComparingTo("0.150000");
        assertThat(bad.outOfControl()).isTrue();
        assertThat(bad.pointUcl()).isNotNull().isLessThan(new BigDecimal("0.15"));

        assertThatThrownBy(() -> spcService.addPoints(pChart, new AddPointsRequest(null, 10, 11, null)))
                .isInstanceOf(BusinessRuleException.class);

        Long cChart = spcService.create(new SaveChartRequest(p.getId(), "Burrs per spring", SpcChartType.C, null,
                null, null)).summary().id();
        for (int count : new int[] { 4, 6, 5, 5, 5 }) {
            spcService.addPoints(cChart, new AddPointsRequest(null, null, count, null));
        }
        assertThat(spcService.get(cChart).ucl()).isEqualByComparingTo("11.708204");
        ChartDetailDto cFlagged = spcService.addPoints(cChart, new AddPointsRequest(null, null, 20, null));
        assertThat(cFlagged.points().get(cFlagged.points().size() - 1).outOfControl()).isTrue();
        assertThat(alertCount(cChart)).isEqualTo(1);
    }

    @Test
    void capability_uses_the_charts_spec_limits_and_rejects_attribute_charts() {
        Product p = td.product();
        Long id = xbarChart(p, "Wire diameter").summary().id();
        spcService.addPoints(id, new AddPointsRequest(values("9.8", "10.0", "10.2", "10.0", "10.0", "10.0"),
                null, null, null));

        CapabilityDto c = spcService.capability(id, null, null);

        assertThat(c.count()).isEqualTo(6);
        assertThat(c.mean()).isEqualByComparingTo("10.000000");
        assertThat(c.cp()).isNotNull();
        assertThat(c.cpk()).isLessThanOrEqualTo(c.cp());

        spcService.update(id, new UpdateChartRequest(new BigDecimal("10.2"), new BigDecimal("9.9"), null));
        assertThat(spcService.capability(id, null, null).cpk()).isLessThan(c.cpk());
        assertThat(auditCount("SPC_CHART_UPDATE", "SpcControlChart", id)).isEqualTo(1);

        Long attribute = spcService.create(new SaveChartRequest(p.getId(), "Burrs", SpcChartType.C, null, null, null))
                .summary().id();
        assertThatThrownBy(() -> spcService.capability(attribute, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void only_one_active_chart_per_product_and_parameter_and_spec_limits_must_be_ordered() {
        Product p = td.product();
        xbarChart(p, "Wire diameter");
        assertThatThrownBy(() -> xbarChart(p, "Wire diameter")).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));
        assertThatThrownBy(() -> spcService.create(new SaveChartRequest(p.getId(), "Other", SpcChartType.XBAR_R, 2,
                new BigDecimal("1"), new BigDecimal("2")))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> spcService.create(new SaveChartRequest(p.getId(), "Other", SpcChartType.XBAR_R, 11,
                null, null))).isInstanceOf(BusinessRuleException.class);
    }

    // ---- SPC fed from inspections (R16-4.2) --------------------------------------------------------------------------

    @Test
    void submitted_inspection_results_update_the_chart_once_per_inspection() {
        Product p = td.product();
        Long id = xbarChart(p, "Wire diameter").summary().id();
        FgBatch batch = fgBatch(p);

        QualityInspection first = finalInspection(batch, "Wire diameter", "Free length");
        submit(first, "10", "0", "10.1", "50.0");
        assertThat(spcService.get(id).summary().pointCount()).as("one value so far").isEqualTo(1);

        QualityInspection second = finalInspection(batch, "Wire diameter");
        submit(second, "10", "0", "10.3");
        ChartDetailDto detail = spcService.get(id);
        assertThat(detail.summary().pointCount()).isEqualTo(2);
        assertThat(detail.points().get(1).subgroupMean()).isEqualByComparingTo("10.200000");
        assertThat(detail.points().get(1).subgroupRange()).isEqualByComparingTo("0.200000");
        assertThat(detail.points()).extracting(PointDto::inspectionId)
                .containsExactly(first.getId(), second.getId());

        submit(second, "10", "0", "10.3");
        assertThat(spcService.get(id).summary().pointCount()).as("resubmitting is idempotent").isEqualTo(2);
    }

    @Test
    void inspections_of_other_products_or_parameters_do_not_feed_the_chart() {
        Product p = td.product();
        Long id = xbarChart(p, "Wire diameter").summary().id();

        QualityInspection otherParameter = finalInspection(fgBatch(p), "Free length");
        submit(otherParameter, "10", "0", "50.0");
        QualityInspection otherProduct = finalInspection(fgBatch(td.product()), "Wire diameter");
        submit(otherProduct, "10", "0", "10.0");

        assertThat(spcService.get(id).summary().pointCount()).isZero();
    }

    @Test
    void an_attribute_chart_takes_the_inspections_rejected_and_inspected_counts() {
        Product p = td.product();
        Long id = spcService.create(new SaveChartRequest(p.getId(), "Surface", SpcChartType.P, null, null, null))
                .summary().id();
        QualityInspection insp = finalInspection(fgBatch(p), "Surface");
        submit(insp, "8", "2", "1");

        ChartDetailDto detail = spcService.get(id);
        assertThat(detail.points()).hasSize(1);
        assertThat(detail.points().get(0).sampleSize()).isEqualTo(10);
        assertThat(detail.points().get(0).defectCount()).isEqualTo(2);
        assertThat(detail.points().get(0).measuredValue()).isEqualByComparingTo("0.200000");
    }

    // ---- CAPA -----------------------------------------------------------------------------------------------------------

    private CapaDto newCapa() {
        return capaService.create(new SaveCapaRequest("Coil rejects", "Rising reject rate",
                CapaSourceType.INTERNAL_AUDIT, null, null, null, null, null, null));
    }

    private SaveCapaRequest withActions(CapaDto c, String rootCause, String corrective) {
        return new SaveCapaRequest(c.title(), c.description(), c.sourceType(), c.sourceId(), rootCause, corrective,
                "Retrain operators", LocalDate.now().plusDays(30), userId);
    }

    @Test
    void a_capa_moves_through_its_states_with_each_precondition_and_is_audited() {
        CapaDto capa = newCapa();
        assertThat(capa.capaNumber()).startsWith("CAPA/");
        assertThat(capa.status()).isEqualTo(CapaStatus.OPEN);
        Long id = capa.id();

        assertThat(capaService.advance(id, new AdvanceCapaRequest(CapaStatus.ROOT_CAUSE_ANALYSIS, null)).status())
                .isEqualTo(CapaStatus.ROOT_CAUSE_ANALYSIS);
        assertThatThrownBy(() -> capaService.advance(id, new AdvanceCapaRequest(CapaStatus.ACTION_IN_PROGRESS, null)))
                .as("no action without a root cause").isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("root cause");

        capaService.update(id, withActions(capa, "Worn coiling tool", null));
        capaService.advance(id, new AdvanceCapaRequest(CapaStatus.ACTION_IN_PROGRESS, null));
        assertThatThrownBy(() -> capaService.advance(id, new AdvanceCapaRequest(CapaStatus.VERIFICATION, null)))
                .as("no verification without a corrective action").isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("corrective");

        capaService.update(id, withActions(capa, "Worn coiling tool", "Replace tool every 5,000 pieces"));
        capaService.advance(id, new AdvanceCapaRequest(CapaStatus.VERIFICATION, null));
        assertThatThrownBy(() -> capaService.advance(id, new AdvanceCapaRequest(CapaStatus.CLOSED, null)))
                .as("closing is a separate action").isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> capaService.advance(id, new AdvanceCapaRequest(CapaStatus.ACTION_IN_PROGRESS, " ")))
                .as("a failed verification needs a reason").isInstanceOf(BusinessRuleException.class);
        capaService.advance(id, new AdvanceCapaRequest(CapaStatus.ACTION_IN_PROGRESS, "Rejects persisted"));
        capaService.advance(id, new AdvanceCapaRequest(CapaStatus.VERIFICATION, null));

        CapaDto closed = capaService.close(id, new CloseCapaRequest("Reject rate back under 0.5% for 4 weeks"));
        assertThat(closed.status()).isEqualTo(CapaStatus.CLOSED);
        assertThat(closed.verifiedBy()).isEqualTo(userId);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.verificationNotes()).contains("0.5%");
        assertThat(closed.nextStatuses()).isEmpty();

        assertThatThrownBy(() -> capaService.update(id, withActions(capa, "x", "y")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> capaService.close(id, new CloseCapaRequest("again")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(auditCount("CAPA_CLOSE", "CapaRecord", id)).isEqualTo(1);
        assertThat(auditCount("CAPA_ADVANCE", "CapaRecord", id)).isEqualTo(5);
    }

    @Test
    void a_rejection_event_raises_one_open_capa() {
        Product p = td.product();
        Rejection r = new Rejection();
        r.setRejectionReasonId(reasonRepo.findAll().get(0).getId());
        r.setItemType("PRODUCT");
        r.setProductId(p.getId());
        r.setQuantity(new BigDecimal("12.000"));
        r.setRecordedBy(userId);
        r = rejectionRepo.save(r);

        CapaDto capa = capaService.fromRejection(r.getId());

        assertThat(capa.sourceType()).isEqualTo(CapaSourceType.REJECTION_ANALYSIS);
        assertThat(capa.sourceId()).isEqualTo(r.getId());
        assertThat(capa.status()).isEqualTo(CapaStatus.OPEN);
        assertThat(capa.title()).startsWith("Rejection " + r.getId());
        assertThat(capa.description()).contains("12");
        final Long rejectionId = r.getId();
        assertThatThrownBy(() -> capaService.fromRejection(rejectionId)).isInstanceOfSatisfying(
                BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));
        assertThatThrownBy(() -> capaService.fromRejection(999_999_999L))
                .isInstanceOf(com.mfg.ims.common.exception.NotFoundException.class);
    }

    @Test
    void a_capa_can_cite_only_an_out_of_control_spc_point_and_only_once() {
        Product p = td.product();
        Long id = xbarChart(p, "Wire diameter").summary().id();
        establishLimits(id);
        ChartDetailDto flagged = addSubgroup(id, "10.5", "10.6");
        PointDto bad = flagged.points().get(flagged.points().size() - 1);
        PointDto fine = flagged.points().get(0);

        CapaDto capa = capaService.fromSpcPoint(bad.id());

        assertThat(capa.sourceType()).isEqualTo(CapaSourceType.SPC_VIOLATION);
        assertThat(capa.sourceId()).isEqualTo(bad.id());
        assertThat(capa.title()).contains("Wire diameter").contains("NELSON_R1");
        assertThatThrownBy(() -> capaService.fromSpcPoint(bad.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> capaService.fromSpcPoint(fine.id())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not out of control");
    }

    @Test
    void a_capa_for_a_complaint_must_be_raised_from_the_complaint() {
        assertThatThrownBy(() -> capaService.create(new SaveCapaRequest("From complaint", null,
                CapaSourceType.CUSTOMER_COMPLAINT, 1L, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("from-complaint");
        assertThatThrownBy(() -> capaService.create(new SaveCapaRequest("No source", null,
                CapaSourceType.REJECTION_ANALYSIS, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- complaints ----------------------------------------------------------------------------------------------------

    private SaveComplaintRequest complaint(Customer c, Long productId, Long batchId) {
        return new SaveComplaintRequest(c.getId(), productId, batchId, LocalDate.now(), ComplaintCategory.DIMENSIONAL,
                "Free length short by 1 mm", "CUST-REF-7", new BigDecimal("40.000"));
    }

    @Test
    void a_complaint_is_linked_to_its_fg_batch_traced_worked_to_closure_and_reported() {
        Customer customer = td.customer();
        Product p = td.product();
        FgBatch batch = fgBatch(p);

        ComplaintDto created = complaintService.create(complaint(customer, null, batch.getId()));

        assertThat(created.complaintNumber()).startsWith("CMP/");
        assertThat(created.status()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(created.productId()).as("the product comes from the batch").isEqualTo(p.getId());
        assertThat(created.fgBatchNumber()).isEqualTo(batch.getBatchNumber());
        assertThat(created.receivedBy()).isEqualTo(userId);
        assertThat(complaintService.trace(created.id())).as("traceability view of the linked FG batch").isNotNull();

        assertThatThrownBy(() -> complaintService.create(complaint(customer, td.product().getId(), batch.getId())))
                .as("product must match the batch").isInstanceOf(BusinessRuleException.class);

        Long id = created.id();
        assertThatThrownBy(() -> complaintService.transition(id,
                new TransitionComplaintRequest(ComplaintStatus.RESOLVED, null, null, null)))
                .as("cannot skip investigation").isInstanceOf(BusinessRuleException.class);
        complaintService.transition(id, new TransitionComplaintRequest(ComplaintStatus.IN_PROGRESS, null, null, null));
        assertThatThrownBy(() -> complaintService.transition(id,
                new TransitionComplaintRequest(ComplaintStatus.RESOLVED, null, null, null)))
                .as("resolving needs disposition and notes").isInstanceOf(BusinessRuleException.class);

        CapaDto capa = capaService.fromComplaint(id);
        assertThat(capa.sourceType()).isEqualTo(CapaSourceType.CUSTOMER_COMPLAINT);
        assertThat(capa.sourceLabel()).isEqualTo(created.complaintNumber());
        assertThat(complaintService.get(id).capaNumber()).isEqualTo(capa.capaNumber());
        assertThatThrownBy(() -> capaService.fromComplaint(id)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));

        ComplaintDto resolved = complaintService.transition(id, new TransitionComplaintRequest(
                ComplaintStatus.RESOLVED, ComplaintDisposition.REPLACEMENT, new BigDecimal("40.000"),
                "Replaced with batch FG-NEW"));
        assertThat(resolved.disposition()).isEqualTo(ComplaintDisposition.REPLACEMENT);
        assertThat(resolved.quantityReturned()).isEqualByComparingTo("40.000");
        ComplaintDto closed = complaintService.transition(id,
                new TransitionComplaintRequest(ComplaintStatus.CLOSED, null, null, null));
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.nextStatuses()).isEmpty();
        assertThatThrownBy(() -> complaintService.update(id, complaint(customer, null, batch.getId())))
                .isInstanceOf(BusinessRuleException.class);

        List<java.util.Map<String, Object>> rows = complaintsReport.runQuery(new ReportRequest(null, null, null,
                null, 0, 500));
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.get("Complaint Number")).isEqualTo(created.complaintNumber());
            assertThat(r.get("FG Batch")).isEqualTo(batch.getBatchNumber());
            assertThat(r.get("Status")).isEqualTo("CLOSED");
            assertThat(r.get("CAPA")).isEqualTo(capa.capaNumber());
        });
        assertThat(complaintsReport.runQuery(new ReportRequest(LocalDate.now().plusDays(1), null, null, null, 0, 500)))
                .noneSatisfy(r -> assertThat(r.get("Complaint Number")).isEqualTo(created.complaintNumber()));
        assertThat(complaintsReport.runQuery(new ReportRequest(null, null, null, "OPEN", 0, 500)))
                .noneSatisfy(r -> assertThat(r.get("Complaint Number")).isEqualTo(created.complaintNumber()));
    }

    @Test
    void a_complaint_without_a_batch_has_no_trace() {
        ComplaintDto c = complaintService.create(complaint(td.customer(), null, null));
        assertThatThrownBy(() -> complaintService.trace(c.id())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not linked");
    }

    // ---- first article inspection ---------------------------------------------------------------------------------------

    private InspectionPlan finalPlan(Product p) {
        InspectionPlan plan = new InspectionPlan();
        plan.setPlanCode(td.code("PLAN"));
        plan.setInspectionType("FINAL");
        plan.setProductId(p.getId());
        plan.setActive(true);
        int seq = 1;
        for (String[] spec : new String[][] { { "Free length", "49.5", "50.5" }, { "Wire diameter", "2.97", "3.03" } }) {
            InspectionPlanParameter param = new InspectionPlanParameter();
            param.setPlan(plan);
            param.setSeq(seq++);
            param.setParameterName(spec[0]);
            param.setLowerLimit(new BigDecimal(spec[1]));
            param.setUpperLimit(new BigDecimal(spec[2]));
            param.setUnit("mm");
            param.setMandatory(true);
            plan.getParameters().add(param);
        }
        return planRepo.save(plan);
    }

    private Long productionOrder(Product p) {
        Long bomId = td.draftBom(p.getId()).getId();
        return jdbc.queryForObject("""
                INSERT INTO ims.production_orders (order_number, product_id, bom_id, planned_qty)
                VALUES (?, ?, ?, 100) RETURNING id
                """, Long.class, td.code("PO"), p.getId(), bomId);
    }

    /** Enters the FAI's measured values in plan order (Free length, Wire diameter). */
    private FaiReportDto enterResults(Long faiId, Long inspectionId, String freeLength, String wireDiameter) {
        List<QualityInspectionItem> items = itemRepo.findByInspectionId(inspectionId).stream()
                .sorted(java.util.Comparator.comparing(QualityInspectionItem::getSeq)).toList();
        return faiService.submitResults(faiId, new SubmitFaiResultsRequest(List.of(
                new FaiResultValue(items.get(0).getId(), new BigDecimal(freeLength)),
                new FaiResultValue(items.get(1).getId(), new BigDecimal(wireDiameter)))));
    }

    private EcoDto implementedEcoFor(Product p) {
        EcoDto eco = ecoService.create(new SaveEcoRequest("FAI trigger " + td.code("t"), "desc", "Tool change",
                EcoChangeCategory.SPEC_CHANGE, null, null, "Low impact",
                List.of(new SaveEcoItemRequest(EcoItemType.PRODUCT, p.getId(), "New coiling tool"))));
        ecoService.submit(eco.id());
        ecoService.approve(eco.id());
        return ecoService.implement(eco.id());
    }

    @Test
    void an_implemented_eco_requires_one_fai_per_product_and_never_a_second_while_one_is_open() {
        Product p = td.product();
        EcoDto first = implementedEcoFor(p);

        assertThat(faiRepo.existsByProductIdAndStatus(p.getId(), FaiStatus.REQUIRED)).isTrue();
        var fai = faiRepo.search("", "REQUIRED", org.springframework.data.domain.Pageable.unpaged()).stream()
                .filter(f -> f.getProductId().equals(p.getId())).toList();
        assertThat(fai).hasSize(1);
        assertThat(fai.get(0).getTriggerType()).isEqualTo(FaiTrigger.ECO_IMPLEMENTED);
        assertThat(fai.get(0).getTriggerReference()).isEqualTo(first.ecoNumber());
        assertThat(fai.get(0).getEcoId()).isEqualTo(first.id());
        assertThat(fai.get(0).getFaiNumber()).startsWith("FAI/");

        implementedEcoFor(p);
        assertThat(faiRepo.findAll().stream().filter(f -> f.getProductId().equals(p.getId())).count())
                .as("a second ECO does not stack another FAI").isEqualTo(1);
        assertThatThrownBy(() -> faiService.createManual(p.getId())).isInstanceOfSatisfying(
                BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));
    }

    @Test
    void a_product_approved_for_the_first_time_requires_an_fai() {
        Product p = td.product();
        jdbc.update("UPDATE ims.products SET lifecycle_status = 'PROTOTYPE' WHERE id = ?", p.getId());

        lifecycleService.transition(p.getId(), new TransitionRequest(ProductLifecycleStatus.APPROVED,
                "Design approved"));

        var fai = faiRepo.findAll().stream().filter(f -> f.getProductId().equals(p.getId())).toList();
        assertThat(fai).hasSize(1);
        assertThat(fai.get(0).getTriggerType()).isEqualTo(FaiTrigger.NEW_PRODUCT);
        assertThat(fai.get(0).getStatus()).isEqualTo(FaiStatus.REQUIRED);
    }

    @Test
    void an_fai_links_the_plan_to_the_first_order_runs_the_inspection_and_passes() {
        Product p = td.product();
        InspectionPlan plan = finalPlan(p);
        Long orderId = productionOrder(p);
        FaiDto required = faiService.createManual(p.getId());
        assertThat(required.status()).isEqualTo(FaiStatus.REQUIRED);

        assertThatThrownBy(() -> faiService.start(required.id(), new StartFaiRequest(productionOrder(td.product()))))
                .as("the order must be for the same product").isInstanceOf(BusinessRuleException.class);

        FaiDto started = faiService.start(required.id(), new StartFaiRequest(orderId));
        assertThat(started.status()).isEqualTo(FaiStatus.IN_PROGRESS);
        assertThat(started.inspectionPlanId()).isEqualTo(plan.getId());
        assertThat(started.productionOrderId()).isEqualTo(orderId);
        assertThat(started.inspectionNumber()).isNotNull();
        QualityInspection insp = inspectionRepo.findById(started.inspectionId()).orElseThrow();
        assertThat(insp.getInspectionType()).isEqualTo("FAI");
        assertThat(itemRepo.findByInspectionId(insp.getId())).hasSize(2);

        assertThatThrownBy(() -> faiService.complete(required.id(), new CompleteFaiRequest(null)))
                .as("results must be submitted first").isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inspectionService.accept(insp.getId()))
                .as("the generic accept does not apply to an FAI").isInstanceOf(BusinessRuleException.class);

        FaiReportDto entered = enterResults(required.id(), insp.getId(), "50.1", "3.00");
        assertThat(entered.items()).extracting(i -> i.result()).containsOnly("PASS");
        QualityInspection afterResults = inspectionRepo.findById(insp.getId()).orElseThrow();
        assertThat(afterResults.getAcceptedQty()).isEqualByComparingTo("1");
        assertThat(afterResults.getRejectedQty()).isEqualByComparingTo("0");

        FaiDto passed = faiService.complete(required.id(), new CompleteFaiRequest("All characteristics in tolerance"));

        assertThat(passed.status()).isEqualTo(FaiStatus.PASSED);
        assertThat(passed.resultSummary()).isEqualTo("All characteristics in tolerance");
        assertThat(passed.completedBy()).isEqualTo(userId);
        assertThat(passed.completedAt()).isNotNull();
        assertThat(inspectionRepo.findById(insp.getId()).orElseThrow().getStatus()).isEqualTo("APPROVED");
        assertThat(auditCount("FAI_COMPLETE", "FirstArticleInspection", required.id())).isEqualTo(1);

        FaiReportDto report = faiService.report(required.id());
        assertThat(report.fai().status()).isEqualTo(FaiStatus.PASSED);
        assertThat(report.items()).extracting(i -> i.parameterName()).containsExactly("Free length", "Wire diameter");
        assertThat(report.items()).extracting(i -> i.result()).containsOnly("PASS");
        assertThat(report.items().get(0).actualValue()).isEqualByComparingTo("50.1");
        assertThatThrownBy(() -> faiService.start(required.id(), new StartFaiRequest(orderId)))
                .as("a finished FAI cannot be restarted").isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void an_fai_with_an_out_of_tolerance_value_fails_and_a_new_one_can_be_raised() {
        Product p = td.product();
        finalPlan(p);
        FaiDto required = faiService.createManual(p.getId());
        FaiDto started = faiService.start(required.id(), new StartFaiRequest(productionOrder(p)));
        QualityInspection insp = inspectionRepo.findById(started.inspectionId()).orElseThrow();

        enterResults(required.id(), insp.getId(), "50.9", "3.00");
        assertThat(inspectionRepo.findById(insp.getId()).orElseThrow().getRejectedQty()).isEqualByComparingTo("1");
        FaiDto failed = faiService.complete(required.id(), null);

        assertThat(failed.status()).isEqualTo(FaiStatus.FAILED);
        assertThat(failed.resultSummary()).isEqualTo("1 of 2 parameters passed");
        assertThat(inspectionRepo.findById(insp.getId()).orElseThrow().getStatus()).isEqualTo("REJECTED");
        assertThat(faiService.report(required.id()).items()).extracting(i -> i.result())
                .containsExactly("FAIL", "PASS");

        assertThat(faiService.createManual(p.getId()).status()).isEqualTo(FaiStatus.REQUIRED);
    }

    @Test
    void an_fai_needs_an_active_inspection_plan() {
        Product p = td.product();
        FaiDto required = faiService.createManual(p.getId());
        assertThatThrownBy(() -> faiService.start(required.id(), new StartFaiRequest(productionOrder(p))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inspection plan");
        assertThat(faiService.get(required.id()).status()).as("a failed start leaves it REQUIRED")
                .isEqualTo(FaiStatus.REQUIRED);
    }

    @Test
    void fai_results_must_belong_to_the_inspection_and_only_be_entered_while_it_is_in_progress() {
        Product p = td.product();
        finalPlan(p);
        FaiDto required = faiService.createManual(p.getId());
        assertThatThrownBy(() -> faiService.submitResults(required.id(), new SubmitFaiResultsRequest(
                List.of(new FaiResultValue(1L, BigDecimal.ONE)))))
                .as("not started yet").isInstanceOf(BusinessRuleException.class);

        FaiDto started = faiService.start(required.id(), new StartFaiRequest(productionOrder(p)));
        QualityInspection other = finalInspection(fgBatch(td.product()), "Free length");
        Long foreignItem = itemRepo.findByInspectionId(other.getId()).get(0).getId();
        assertThatThrownBy(() -> faiService.submitResults(required.id(), new SubmitFaiResultsRequest(
                List.of(new FaiResultValue(foreignItem, BigDecimal.ONE)))))
                .as("an item of another inspection is refused").isInstanceOf(BusinessRuleException.class);

        enterResults(required.id(), started.inspectionId(), "50.0", "3.00");
        faiService.complete(required.id(), null);
        assertThatThrownBy(() -> enterResults(required.id(), started.inspectionId(), "50.0", "3.00"))
                .as("finished FAIs are frozen").isInstanceOf(BusinessRuleException.class);
    }
}
