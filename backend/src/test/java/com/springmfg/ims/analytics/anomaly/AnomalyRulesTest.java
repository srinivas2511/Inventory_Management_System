package com.springmfg.ims.analytics.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;

import com.springmfg.ims.analytics.anomaly.AnomalyDetectionService.MachineWindow;
import com.springmfg.ims.analytics.anomaly.AnomalyDetectionService.SweepResult;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.insight.alert.AlertService;

/** The decision rules of each detector with hand-worked figures. */
class AnomalyRulesTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 28);   // the last complete week, a Monday

    /** Twelve weeks before WEEK: six of 10 and six of 12. Mean 11, sum of squares 12, sample sd sqrt(12/11) = 1.0445. */
    private static Map<LocalDate, Double> alternatingHistory() {
        Map<LocalDate, Double> m = new LinkedHashMap<>();
        for (int i = 12; i >= 1; i--) {
            m.put(WEEK.minusWeeks(i), i % 2 == 0 ? 10.0 : 12.0);
        }
        return m;
    }

    @Test
    void consumptionAboveThreeDeviationsIsFlagged() {
        Map<LocalDate, Double> weeks = alternatingHistory();
        weeks.put(WEEK, 14.0);   // z = (14 - 11) / 1.0445 = 2.87
        assertThat(AnomalyDetectionService.consumptionFinding("WIRE-1", weeks, WEEK, 3)).isEmpty();

        weeks.put(WEEK, 15.0);   // z = (15 - 11) / 1.0445 = 3.83
        assertThat(AnomalyDetectionService.consumptionFinding("WIRE-1", weeks, WEEK, 3)).hasValueSatisfying(text ->
                assertThat(text).contains("material WIRE-1").contains("was 15.0")
                        .contains("weekly average of 11.0").contains("3.8 standard deviations"));
    }

    @Test
    void missingWeeksCountAsZeroConsumption() {
        // three weeks of 12 and nine of 0: mean 3, sum of squares 3*81 + 9*9 = 324, sd sqrt(324/11) = 5.427
        Map<LocalDate, Double> weeks = new LinkedHashMap<>();
        weeks.put(WEEK.minusWeeks(12), 12.0);
        weeks.put(WEEK.minusWeeks(6), 12.0);
        weeks.put(WEEK.minusWeeks(1), 12.0);
        weeks.put(WEEK, 12.0);   // z = 9 / 5.427 = 1.66
        assertThat(AnomalyDetectionService.consumptionFinding("M", weeks, WEEK, 3)).isEmpty();
        weeks.put(WEEK, 30.0);   // z = 27 / 5.427 = 4.97
        assertThat(AnomalyDetectionService.consumptionFinding("M", weeks, WEEK, 3)).isPresent();
    }

    @Test
    void aMaterialWithSteadyConsumptionIsNeverFlaggedHoweverBigTheJump() {
        Map<LocalDate, Double> weeks = new LinkedHashMap<>();
        for (int i = 12; i >= 1; i--) {
            weeks.put(WEEK.minusWeeks(i), 10.0);
        }
        weeks.put(WEEK, 100.0);
        assertThat(AnomalyDetectionService.consumptionFinding("M", weeks, WEEK, 3)).isEmpty();
    }

    @Test
    void supplierQualityDropOfTenPointsOrMoreIsFlagged() {
        LocalDate month = LocalDate.of(2026, 10, 1);
        Map<LocalDate, Double> pass = new LinkedHashMap<>();
        pass.put(month.minusMonths(3), 98.0);
        pass.put(month.minusMonths(2), 97.0);
        pass.put(month.minusMonths(1), 99.0);      // baseline mean 98.0
        pass.put(month, 70.0);                     // 28 points down
        assertThat(AnomalyDetectionService.supplierFinding("Acme Steel", pass, month, 10)).hasValueSatisfying(t ->
                assertThat(t).contains("Acme Steel").contains("70.0%").contains("98.0%").contains("3 months"));

        pass.put(month, 88.0);                     // exactly 10 points down: flagged (>=)
        assertThat(AnomalyDetectionService.supplierFinding("Acme Steel", pass, month, 10)).isPresent();
        pass.put(month, 89.0);                     // 9 points
        assertThat(AnomalyDetectionService.supplierFinding("Acme Steel", pass, month, 10)).isEmpty();
    }

    @Test
    void supplierNeedsTwoEarlierMonthsAndACurrentOne() {
        LocalDate month = LocalDate.of(2026, 10, 1);
        Map<LocalDate, Double> oneEarlier = new LinkedHashMap<>();
        oneEarlier.put(month.minusMonths(1), 99.0);
        oneEarlier.put(month, 10.0);
        assertThat(AnomalyDetectionService.supplierFinding("S", oneEarlier, month, 10)).isEmpty();

        Map<LocalDate, Double> noCurrent = new LinkedHashMap<>();
        noCurrent.put(month.minusMonths(2), 99.0);
        noCurrent.put(month.minusMonths(1), 99.0);
        assertThat(AnomalyDetectionService.supplierFinding("S", noCurrent, month, 10)).isEmpty();
    }

    @Test
    void machineYieldDrop() {
        // recent 400 good / 100 bad = 80.0 %; baseline 1900 good / 100 bad = 95.0 %: down 15 points
        MachineWindow w = new MachineWindow("CNC-1", 400, 100, 1900, 100, 0, 0);
        assertThat(AnomalyDetectionService.yieldFinding(w, 5)).hasValueSatisfying(t ->
                assertThat(t).contains("CNC-1").contains("80.0%").contains("95.0%"));
        assertThat(AnomalyDetectionService.yieldFinding(w, 16)).isEmpty();
        // too few pieces in the recent window to compare
        assertThat(AnomalyDetectionService.yieldFinding(new MachineWindow("CNC-1", 30, 10, 1900, 100, 0, 0), 5)).isEmpty();
        // a small drop: recent 91 / 100 = 91.0 % against 95.0 % is 4 points
        assertThat(AnomalyDetectionService.yieldFinding(new MachineWindow("CNC-1", 91, 9, 1900, 100, 0, 0), 5)).isEmpty();
    }

    @Test
    void machineDowntimeJump() {
        // recent 420 min / 7 days = 60.0/day; baseline 560 min / 28 days = 20.0/day; factor 2 -> 60 >= 40 and >= 30
        MachineWindow w = new MachineWindow("CNC-2", 0, 0, 0, 0, 420, 560);
        assertThat(AnomalyDetectionService.downtimeFinding(w, 2, 30)).hasValueSatisfying(t ->
                assertThat(t).contains("CNC-2").contains("60.0 min/day").contains("20.0 min/day"));
        // 175 / 7 = 25 min/day is below the 30 minute floor
        assertThat(AnomalyDetectionService.downtimeFinding(new MachineWindow("CNC-2", 0, 0, 0, 0, 175, 0), 2, 30)).isEmpty();
        // 30 min/day against a baseline of none is flagged; against 20/day it is not 2x
        assertThat(AnomalyDetectionService.downtimeFinding(new MachineWindow("CNC-2", 0, 0, 0, 0, 210, 0), 2, 30)).isPresent();
        assertThat(AnomalyDetectionService.downtimeFinding(new MachineWindow("CNC-2", 0, 0, 0, 0, 210, 560), 2, 30)).isEmpty();
    }

    @Test
    void aFailingDetectorIsReportedAndDoesNotStopTheOthers() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doThrow(new org.springframework.jdbc.BadSqlGrammarException("test", "SELECT", new java.sql.SQLException("boom")))
                .when(jdbc).query(org.mockito.ArgumentMatchers.anyString(), any(SqlParameterSource.class),
                        any(RowCallbackHandler.class));
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getDecimal(org.mockito.ArgumentMatchers.anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(settings.getInt(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(i -> i.getArgument(1));
        var service = new AnomalyDetectionService(jdbc, mock(AlertService.class), settings,
                mock(PlatformTransactionManager.class));

        SweepResult result = service.sweep(LocalDate.of(2026, 10, 5));

        assertThat(result.failedDetectors())
                .containsExactly("consumption", "supplier-quality", "machine-yield", "machine-downtime");
        assertThat(result.raised()).isZero();
    }
}
