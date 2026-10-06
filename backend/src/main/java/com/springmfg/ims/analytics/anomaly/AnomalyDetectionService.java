package com.springmfg.ims.analytics.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.insight.alert.AlertService;

/**
 * Anomaly sweep (R19-3.3, ANA-004): unusual stock consumption, a supplier whose incoming quality fell, and a machine
 * whose yield dropped or whose unplanned downtime jumped. Each finding is an {@code ANOMALY_DETECTED} alert on its own
 * entity type, so the detectors never overwrite one another, and a repeat finding refreshes the open alert instead of
 * creating another. Thresholds are system settings. Each detector runs in its own transaction, so one failing
 * (even with a database error) is logged and does not stop the others. The decision rules are pure functions so they
 * can be tested with hand-worked numbers; the SQL only gathers the figures.
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    public static final String ALERT_TYPE = "ANOMALY_DETECTED";
    static final int HISTORY_WEEKS = 12;
    static final int RECENT_DAYS = 7;
    static final int BASELINE_DAYS = 28;
    /** A machine needs at least this many pieces in both windows before its yield is compared. */
    static final int MIN_PIECES = 50;

    public record SweepResult(int raised, List<String> findings, List<String> failedDetectors) {
    }

    /** Output, scrap and downtime of one machine over the recent and baseline windows. */
    record MachineWindow(String code, double goodRecent, double badRecent, double goodBase, double badBase,
                         double downRecent, double downBase) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertService alerts;
    private final SystemSettingService settings;
    private final TransactionTemplate perDetector;

    public AnomalyDetectionService(NamedParameterJdbcTemplate jdbc, AlertService alerts, SystemSettingService settings,
                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.alerts = alerts;
        this.settings = settings;
        this.perDetector = new TransactionTemplate(transactionManager);
        this.perDetector.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public SweepResult sweep() {
        return sweep(LocalDate.now());
    }

    public SweepResult sweep(LocalDate today) {
        List<String> findings = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        run("consumption", failed, findings, () -> consumption(today));
        run("supplier-quality", failed, findings, () -> supplierQuality(today));
        run("machine-yield", failed, findings, () -> machineYield(today));
        run("machine-downtime", failed, findings, () -> machineDowntime(today));
        return new SweepResult(findings.size(), findings, failed);
    }

    private void run(String name, List<String> failed, List<String> findings, java.util.function.Supplier<List<String>> detector) {
        try {
            List<String> found = perDetector.execute(status -> detector.get());
            if (found != null) {
                findings.addAll(found);
            }
        } catch (RuntimeException e) {
            log.error("Anomaly detector {} failed: {}", name, e.toString());
            failed.add(name);
        }
    }

    // ── Pure decision rules ──────────────────────────────────────────────────────────────────────────────────────

    /** Last complete week against the {@link #HISTORY_WEEKS} weeks before it, zero-filled. */
    static Optional<String> consumptionFinding(String code, Map<LocalDate, Double> perWeek, LocalDate week, double z) {
        List<Double> history = new ArrayList<>();
        for (LocalDate w = week.minusWeeks(HISTORY_WEEKS); w.isBefore(week); w = w.plusWeeks(1)) {
            history.add(perWeek.getOrDefault(w, 0.0));
        }
        double current = perWeek.getOrDefault(week, 0.0);
        if (!AnomalyMath.isSpike(current, history, z)) {
            return Optional.empty();
        }
        double mean = AnomalyMath.mean(history);
        double score = AnomalyMath.zScore(current, mean, AnomalyMath.sampleStdDev(history));
        return Optional.of("Consumption of material " + code + " in the week of " + week + " was " + round(current)
                + " against a weekly average of " + round(mean) + " over the previous " + HISTORY_WEEKS
                + " weeks (" + round(score) + " standard deviations above).");
    }

    /** This month's pass rate against the mean of the earlier months; needs at least two earlier months. */
    static Optional<String> supplierFinding(String name, Map<LocalDate, Double> perMonth, LocalDate thisMonth,
                                            double dropPts) {
        Double current = perMonth.get(thisMonth);
        List<Double> prior = new ArrayList<>();
        perMonth.forEach((m, v) -> {
            if (m.isBefore(thisMonth)) {
                prior.add(v);
            }
        });
        if (current == null || prior.size() < 2) {
            return Optional.empty();
        }
        double baseline = AnomalyMath.mean(prior);
        if (AnomalyMath.dropPoints(baseline, current) < dropPts) {
            return Optional.empty();
        }
        return Optional.of("Incoming quality pass rate of supplier " + name + " this month is " + round(current)
                + "% against " + round(baseline) + "% over the previous " + prior.size() + " months.");
    }

    static Optional<String> yieldFinding(MachineWindow w, double dropPts) {
        double recentTotal = w.goodRecent() + w.badRecent();
        double baseTotal = w.goodBase() + w.badBase();
        if (recentTotal < MIN_PIECES || baseTotal < MIN_PIECES) {
            return Optional.empty();
        }
        double recent = w.goodRecent() * 100.0 / recentTotal;
        double base = w.goodBase() * 100.0 / baseTotal;
        if (AnomalyMath.dropPoints(base, recent) < dropPts) {
            return Optional.empty();
        }
        return Optional.of("First-pass yield of machine " + w.code() + " over the last " + RECENT_DAYS + " days is "
                + round(recent) + "% against " + round(base) + "% in the " + BASELINE_DAYS + " days before.");
    }

    static Optional<String> downtimeFinding(MachineWindow w, double factor, double minMinutesPerDay) {
        double recentPerDay = w.downRecent() / RECENT_DAYS;
        double basePerDay = w.downBase() / BASELINE_DAYS;
        if (recentPerDay < minMinutesPerDay || recentPerDay < basePerDay * factor) {
            return Optional.empty();
        }
        return Optional.of("Unplanned downtime of machine " + w.code() + " averaged " + round(recentPerDay)
                + " min/day over the last " + RECENT_DAYS + " days against " + round(basePerDay) + " min/day in the "
                + BASELINE_DAYS + " days before.");
    }

    // ── Detectors: gather the figures, apply the rule, raise the alert ────────────────────────────────────────────

    List<String> consumption(LocalDate today) {
        double z = settings.getDecimal("analytics.anomaly.z_threshold", new BigDecimal("3")).doubleValue();
        int minWeeks = Math.max(2, settings.getInt("analytics.anomaly.min_history_weeks", 6));
        LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        LocalDate historyStart = week.minusWeeks(HISTORY_WEEKS);
        LocalDate eligibleBefore = week.minusWeeks(minWeeks);

        Map<Long, String> codes = new LinkedHashMap<>();
        Map<Long, Map<LocalDate, Double>> weekly = new LinkedHashMap<>();
        jdbc.query("""
                SELECT it.material_id, m.material_code, CAST(date_trunc('week', it.transaction_date) AS date) AS week,
                       SUM(ABS(it.quantity)) AS qty
                  FROM ims.inventory_transactions it
                  JOIN ims.materials m ON m.id = it.material_id
                 WHERE it.transaction_type = 'MATERIAL_ISSUE' AND it.item_type = 'MATERIAL' AND it.quantity < 0
                   AND it.transaction_date >= CAST(:start AS date) AND it.transaction_date < CAST(:until AS date)
                   AND it.material_id IN (SELECT material_id FROM ims.inventory_transactions
                                           WHERE transaction_type = 'MATERIAL_ISSUE' AND item_type = 'MATERIAL'
                                           GROUP BY material_id HAVING MIN(transaction_date) < CAST(:eligible AS date))
                 GROUP BY it.material_id, m.material_code, date_trunc('week', it.transaction_date)
                """, new MapSqlParameterSource("start", Date.valueOf(historyStart))
                        .addValue("until", Date.valueOf(week.plusWeeks(1)))
                        .addValue("eligible", Date.valueOf(eligibleBefore)), rs -> {
            long id = rs.getLong("material_id");
            codes.put(id, rs.getString("material_code"));
            weekly.computeIfAbsent(id, k -> new LinkedHashMap<>())
                    .put(rs.getDate("week").toLocalDate(), rs.getBigDecimal("qty").doubleValue());
        });

        List<String> findings = new ArrayList<>();
        weekly.forEach((id, perWeek) -> consumptionFinding(codes.get(id), perWeek, week, z).ifPresent(text -> {
            alerts.raise(ALERT_TYPE, "MEDIUM", "MATERIAL_CONSUMPTION", id, text);
            findings.add(text);
        }));
        return findings;
    }

    List<String> supplierQuality(LocalDate today) {
        double dropPts = settings.getDecimal("analytics.anomaly.supplier_quality_drop_pts", new BigDecimal("10")).doubleValue();
        LocalDate thisMonth = today.withDayOfMonth(1);
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Map<LocalDate, Double>> pass = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.supplier_id, s.name, p.month, p.quality_pass_pct
                  FROM ims.mv_supplier_performance p JOIN ims.suppliers s ON s.id = p.supplier_id
                 WHERE p.month >= CAST(:from AS date) AND p.month <= CAST(:to AS date) AND p.quality_pass_pct IS NOT NULL
                """, new MapSqlParameterSource("from", Date.valueOf(thisMonth.minusMonths(3)))
                        .addValue("to", Date.valueOf(thisMonth)), rs -> {
            long id = rs.getLong("supplier_id");
            names.put(id, rs.getString("name"));
            pass.computeIfAbsent(id, k -> new LinkedHashMap<>())
                    .put(rs.getDate("month").toLocalDate(), rs.getBigDecimal("quality_pass_pct").doubleValue());
        });
        List<String> findings = new ArrayList<>();
        pass.forEach((id, perMonth) -> supplierFinding(names.get(id), perMonth, thisMonth, dropPts).ifPresent(text -> {
            alerts.raise(ALERT_TYPE, "HIGH", "SUPPLIER_QUALITY", id, text);
            findings.add(text);
        }));
        return findings;
    }

    private Map<Long, MachineWindow> machineWindows(LocalDate today) {
        LocalDate recentFrom = today.minusDays(RECENT_DAYS - 1L);
        LocalDate baseFrom = recentFrom.minusDays(BASELINE_DAYS);
        Map<Long, MachineWindow> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT o.machine_id, m.machine_code,
                       COALESCE(SUM(o.good_qty)  FILTER (WHERE o.day >= CAST(:recentFrom AS date)), 0) AS good_recent,
                       COALESCE(SUM(o.bad_qty)   FILTER (WHERE o.day >= CAST(:recentFrom AS date)), 0) AS bad_recent,
                       COALESCE(SUM(o.good_qty)  FILTER (WHERE o.day <  CAST(:recentFrom AS date)), 0) AS good_base,
                       COALESCE(SUM(o.bad_qty)   FILTER (WHERE o.day <  CAST(:recentFrom AS date)), 0) AS bad_base,
                       COALESCE(SUM(o.unplanned_downtime_min) FILTER (WHERE o.day >= CAST(:recentFrom AS date)), 0) AS down_recent,
                       COALESCE(SUM(o.unplanned_downtime_min) FILTER (WHERE o.day <  CAST(:recentFrom AS date)), 0) AS down_base
                  FROM ims.mv_oee_daily o JOIN ims.machines m ON m.id = o.machine_id
                 WHERE o.day >= CAST(:baseFrom AS date) AND o.day <= CAST(:today AS date)
                 GROUP BY o.machine_id, m.machine_code
                """, new MapSqlParameterSource("recentFrom", Date.valueOf(recentFrom))
                        .addValue("baseFrom", Date.valueOf(baseFrom)).addValue("today", Date.valueOf(today)), rs -> {
            out.put(rs.getLong("machine_id"), new MachineWindow(rs.getString("machine_code"),
                    rs.getDouble("good_recent"), rs.getDouble("bad_recent"), rs.getDouble("good_base"),
                    rs.getDouble("bad_base"), rs.getDouble("down_recent"), rs.getDouble("down_base")));
        });
        return out;
    }

    List<String> machineYield(LocalDate today) {
        double dropPts = settings.getDecimal("analytics.anomaly.yield_drop_pts", new BigDecimal("5")).doubleValue();
        List<String> findings = new ArrayList<>();
        machineWindows(today).forEach((id, w) -> yieldFinding(w, dropPts).ifPresent(text -> {
            alerts.raise(ALERT_TYPE, "MEDIUM", "MACHINE_YIELD", id, text);
            findings.add(text);
        }));
        return findings;
    }

    List<String> machineDowntime(LocalDate today) {
        double factor = settings.getDecimal("analytics.anomaly.downtime_factor", new BigDecimal("2")).doubleValue();
        double minMinutes = settings.getInt("analytics.anomaly.min_downtime_min", 30);
        List<String> findings = new ArrayList<>();
        machineWindows(today).forEach((id, w) -> downtimeFinding(w, factor, minMinutes).ifPresent(text -> {
            alerts.raise(ALERT_TYPE, "MEDIUM", "MACHINE_DOWNTIME", id, text);
            findings.add(text);
        }));
        return findings;
    }

    private static String round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
