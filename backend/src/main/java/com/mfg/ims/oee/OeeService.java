package com.mfg.ims.oee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.masterdata.machine.Machine;
import com.mfg.ims.masterdata.machine.MachineRepository;
import com.mfg.ims.oee.OeeCalculator.DayInput;
import com.mfg.ims.oee.OeeCalculator.Result;
import com.mfg.ims.oee.OeeDtos.DowntimeReasonDto;
import com.mfg.ims.oee.OeeDtos.OeeDto;
import com.mfg.ims.oee.OeeDtos.OeeLossDto;
import com.mfg.ims.oee.OeeDtos.OeeLossesDto;
import com.mfg.ims.oee.OeeDtos.OeeTrendPointDto;
import com.mfg.ims.oee.OeeRepository.DailyRow;
import com.mfg.ims.oee.OeeRepository.DowntimeRow;
import com.mfg.ims.prodcalendar.ProductionCalendar;
import com.mfg.ims.prodcalendar.ProductionCalendar.Window;
import com.mfg.ims.production.domain.DowntimeCategory;

/**
 * Overall Equipment Effectiveness per machine and for the plant (DESIGN 24.1).
 *
 * <p>Planned production time is the configured shifts on each working day. A non-working day that nevertheless has
 * recorded output counts as a planned day, so overtime is not divided by zero. Days after today are left out and
 * today only counts the shift time that has already passed, so an unfinished day does not drag OEE down. Every query
 * is bounded to {@link #MAX_RANGE_DAYS} days.
 */
@Service
public class OeeService {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 366;

    private final OeeRepository repo;
    private final MachineRepository machines;
    private final ProductionCalendar calendar;

    public OeeService(OeeRepository repo, MachineRepository machines, ProductionCalendar calendar) {
        this.repo = repo;
        this.machines = machines;
        this.calendar = calendar;
    }

    /** A validated range: {@code to} may lie in the future; {@code end} is the last day that is counted. */
    private record Range(LocalDate from, LocalDate to, LocalDate end, ZoneId zone, Instant now) {
        boolean empty() {
            return end.isBefore(from);
        }

        Instant fromTs() {
            return from.atStartOfDay(zone).toInstant();
        }

        Instant toTs() {
            return end.plusDays(1).atStartOfDay(zone).toInstant();
        }
    }

    /** OEE of one machine over a range of business days. */
    @Transactional(readOnly = true)
    public OeeDto calculateForMachine(Long machineId, LocalDate from, LocalDate to) {
        Machine machine = machines.findById(machineId)
                .orElseThrow(() -> new NotFoundException("Machine not found: " + machineId));
        Range r = range(from, to);
        Map<LocalDate, DailyRow> rows = r.empty() ? Map.of() : byDay(repo.liveDaily(machineId, r.from(), r.end(),
                r.fromTs(), r.toTs()));
        return toDto(machine, r, OeeCalculator.calculate(dayInputs(rows, r)));
    }

    /** OEE of every active machine, ordered by machine code. */
    @Transactional(readOnly = true)
    public List<OeeDto> calculateAll(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        Map<Long, Map<LocalDate, DailyRow>> rows = r.empty() ? Map.of() : groupByMachine(
                repo.liveDaily(0, r.from(), r.end(), r.fromTs(), r.toTs()));
        return activeMachines().stream()
                .map(m -> toDto(m, r, OeeCalculator.calculate(dayInputs(rows.getOrDefault(m.getId(), Map.of()), r))))
                .toList();
    }

    /** OEE of all active machines together: minutes and pieces are summed first, then the ratios are taken. */
    @Transactional(readOnly = true)
    public OeeDto summary(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        Map<Long, Map<LocalDate, DailyRow>> rows = r.empty() ? Map.of() : groupByMachine(
                repo.liveDaily(0, r.from(), r.end(), r.fromTs(), r.toTs()));
        List<DayInput> all = new ArrayList<>();
        for (Machine m : activeMachines()) {
            all.addAll(dayInputs(rows.getOrDefault(m.getId(), Map.of()), r));
        }
        return toDto(null, r, OeeCalculator.calculate(all));
    }

    /** Day-by-day OEE from the materialized view; for one machine, or for all active machines together. */
    @Transactional(readOnly = true)
    public List<OeeTrendPointDto> trend(Long machineId, LocalDate from, LocalDate to) {
        Range r = range(from, to);
        if (r.empty()) {
            return List.of();
        }
        List<Machine> scope = machineId == null ? activeMachines() : List.of(requireMachine(machineId));
        Map<Long, Map<LocalDate, DailyRow>> rows = groupByMachine(
                repo.viewDaily(machineId == null ? 0 : machineId, r.from(), r.end()));
        long shiftMinutes = calendar.shiftMinutesPerDay();
        List<OeeTrendPointDto> points = new ArrayList<>();
        for (LocalDate day = r.from(); !day.isAfter(r.end()); day = day.plusDays(1)) {
            List<DayInput> inputs = new ArrayList<>();
            for (Machine m : scope) {
                inputs.add(dayInput(day, rows.getOrDefault(m.getId(), Map.of()).get(day), r, shiftMinutes));
            }
            Result res = OeeCalculator.calculate(inputs);
            points.add(new OeeTrendPointDto(day, res.availableMinutes(), res.operatingMinutes(), res.goodQty(),
                    res.badQty(), res.availability(), res.performance(), res.quality(), res.oee()));
        }
        return points;
    }

    /** The six big losses, largest first, with the running share for a Pareto chart. */
    @Transactional(readOnly = true)
    public OeeLossesDto losses(Long machineId, LocalDate from, LocalDate to) {
        Range r = range(from, to);
        if (r.empty()) {
            return new OeeLossesDto(r.from(), r.to(), BigDecimal.ZERO, List.of());
        }
        long id = machineId == null ? 0 : requireMachine(machineId).getId();
        Map<Long, Map<LocalDate, DailyRow>> rows = groupByMachine(repo.liveDaily(id, r.from(), r.end(), r.fromTs(),
                r.toTs()));
        List<Machine> scope = machineId == null ? activeMachines() : List.of(requireMachine(machineId));
        List<DayInput> inputs = new ArrayList<>();
        for (Machine m : scope) {
            inputs.addAll(dayInputs(rows.getOrDefault(m.getId(), Map.of()), r));
        }
        Result res = OeeCalculator.calculate(inputs);

        Map<String, BigDecimal> minutes = new LinkedHashMap<>();
        for (DowntimeRow row : repo.downtimeBreakdown(id, r.fromTs(), r.toTs())) {
            if (!DowntimeCategory.parseOrOther(row.category()).isPlanned()) {
                minutes.merge(DowntimeCategory.parseOrOther(row.category()).name(), row.minutes(), BigDecimal::add);
            }
        }
        minutes.merge(DowntimeCategory.PLANNED_MAINTENANCE.name(), res.plannedDowntimeMinutes(), BigDecimal::add);
        minutes.merge("SPEED_LOSS", res.speedLossMinutes(), BigDecimal::add);
        minutes.merge("QUALITY_LOSS", res.badIdealMinutes(), BigDecimal::add);

        BigDecimal total = minutes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map.Entry<String, BigDecimal>> sorted = minutes.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        List<OeeLossDto> items = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : sorted) {
            running = running.add(e.getValue());
            items.add(new OeeLossDto(e.getKey(), lossLabel(e.getKey()), e.getValue().setScale(3, RoundingMode.HALF_UP),
                    share(e.getValue(), total), share(running, total)));
        }
        return new OeeLossesDto(r.from(), r.to(), total.setScale(3, RoundingMode.HALF_UP), items);
    }

    /** The downtime reasons that cost the most minutes in the range. */
    @Transactional(readOnly = true)
    public List<DowntimeReasonDto> topDowntimeReasons(Long machineId, LocalDate from, LocalDate to, int limit) {
        Range r = range(from, to);
        if (r.empty() || limit <= 0) {
            return List.of();
        }
        long id = machineId == null ? 0 : requireMachine(machineId).getId();
        Map<String, DowntimeReasonDto> merged = new HashMap<>();
        for (DowntimeRow row : repo.downtimeBreakdown(id, r.fromTs(), r.toTs())) {
            String key = row.category() + "|" + row.reason();
            merged.merge(key, new DowntimeReasonDto(row.reason(), row.category(), row.occurrences(), row.minutes()),
                    (a, b) -> new DowntimeReasonDto(a.reason(), a.category(), a.occurrences() + b.occurrences(),
                            a.minutes().add(b.minutes())));
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(DowntimeReasonDto::minutes).reversed()
                        .thenComparing(DowntimeReasonDto::reason))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Range range(LocalDate from, LocalDate to) {
        ZoneId zone = calendar.zone();
        Instant now = Instant.now();
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The start of the range " + effectiveFrom + " is after its end " + effectiveTo);
        }
        if (Duration.between(effectiveFrom.atStartOfDay(), effectiveTo.plusDays(1).atStartOfDay()).toDays()
                > MAX_RANGE_DAYS) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A range may span at most " + MAX_RANGE_DAYS + " days");
        }
        LocalDate end = effectiveTo.isAfter(today) ? today : effectiveTo;
        return new Range(effectiveFrom, effectiveTo, end, zone, now);
    }

    private List<DayInput> dayInputs(Map<LocalDate, DailyRow> rows, Range r) {
        if (r.empty()) {
            return List.of();
        }
        long shiftMinutes = calendar.shiftMinutesPerDay();
        List<DayInput> inputs = new ArrayList<>();
        for (LocalDate day = r.from(); !day.isAfter(r.end()); day = day.plusDays(1)) {
            inputs.add(dayInput(day, rows.get(day), r, shiftMinutes));
        }
        return inputs;
    }

    private DayInput dayInput(LocalDate day, DailyRow row, Range r, long shiftMinutes) {
        boolean activity = row != null && (row.idealMinutes().signum() > 0 || row.goodQty().signum() > 0
                || row.badQty().signum() > 0);
        BigDecimal planned = BigDecimal.ZERO;
        if (calendar.isWorkingDay(day) || activity) {
            planned = BigDecimal.valueOf(plannedMinutes(day, r, shiftMinutes));
        }
        if (row == null) {
            return new DayInput(day, planned, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new DayInput(day, planned, row.plannedDowntimeMinutes(), row.unplannedDowntimeMinutes(),
                row.idealMinutes(), row.badIdealMinutes(), row.goodQty(), row.badQty());
    }

    /** A past day counts every shift minute; today counts only the shift time that has already elapsed. */
    private long plannedMinutes(LocalDate day, Range r, long shiftMinutes) {
        LocalDate today = r.now().atZone(r.zone()).toLocalDate();
        if (day.isBefore(today)) {
            return shiftMinutes;
        }
        long elapsed = 0;
        for (Window w : calendar.shiftWindowsOn(day)) {
            Instant until = w.end().isBefore(r.now()) ? w.end() : r.now();
            if (until.isAfter(w.start())) {
                elapsed += Duration.between(w.start(), until).toMinutes();
            }
        }
        return day.isAfter(today) ? 0 : elapsed;
    }

    private OeeDto toDto(Machine machine, Range r, Result res) {
        return new OeeDto(machine == null ? null : machine.getId(), machine == null ? "ALL" : machine.getMachineCode(),
                machine == null ? "All machines" : machine.getName(), r.from(), r.to(), res.availableMinutes(),
                res.plannedDowntimeMinutes(), res.unplannedDowntimeMinutes(), res.operatingMinutes(),
                res.idealMinutes(), res.goodQty(), res.badQty(), res.availability(), res.performance(), res.quality(),
                res.oee());
    }

    private List<Machine> activeMachines() {
        return machines.findAllByActiveTrue().stream()
                .sorted(Comparator.comparing(Machine::getMachineCode)).toList();
    }

    private Machine requireMachine(Long id) {
        return machines.findById(id).orElseThrow(() -> new NotFoundException("Machine not found: " + id));
    }

    private static Map<LocalDate, DailyRow> byDay(List<DailyRow> rows) {
        Map<LocalDate, DailyRow> out = new HashMap<>();
        rows.forEach(row -> out.put(row.day(), row));
        return out;
    }

    private static Map<Long, Map<LocalDate, DailyRow>> groupByMachine(List<DailyRow> rows) {
        Map<Long, Map<LocalDate, DailyRow>> out = new HashMap<>();
        rows.forEach(row -> out.computeIfAbsent(row.machineId(), k -> new HashMap<>()).put(row.day(), row));
        return out;
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        return total.signum() <= 0 ? BigDecimal.ZERO : part.divide(total, 4, RoundingMode.HALF_UP);
    }

    private static String lossLabel(String key) {
        return switch (key) {
            case "SPEED_LOSS" -> "Reduced speed";
            case "QUALITY_LOSS" -> "Rejects and scrap";
            default -> DowntimeCategory.parseOrOther(key).label();
        };
    }
}
