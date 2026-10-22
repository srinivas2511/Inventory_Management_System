package com.mfg.ims.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.masterdata.machine.Machine;
import com.mfg.ims.masterdata.machine.MachineRepository;
import com.mfg.ims.prodcalendar.ProductionCalendar;
import com.mfg.ims.production.domain.ProductionOperation;
import com.mfg.ims.production.repository.ProductionOperationRepository;
import com.mfg.ims.scheduling.SchedulingDtos.GanttBarDto;
import com.mfg.ims.scheduling.SchedulingDtos.GanttDayDto;
import com.mfg.ims.scheduling.SchedulingDtos.GanttDto;
import com.mfg.ims.scheduling.SchedulingDtos.GanttMachineDto;
import com.mfg.ims.scheduling.SchedulingDtos.GanttMaintenanceDto;
import com.mfg.ims.scheduling.SchedulingDtos.OrderOutcomeDto;
import com.mfg.ims.scheduling.SchedulingDtos.PlacementDto;
import com.mfg.ims.scheduling.SchedulingDtos.RescheduleRequest;
import com.mfg.ims.scheduling.SchedulingDtos.RescheduleResultDto;
import com.mfg.ims.scheduling.SchedulingDtos.RunScheduleRequest;
import com.mfg.ims.scheduling.SchedulingDtos.SchedulePlanDto;
import com.mfg.ims.scheduling.SchedulingDtos.UnplacedDto;
import com.mfg.ims.scheduling.SchedulingDtos.UnscheduledOperationDto;
import com.mfg.ims.scheduling.SchedulingRepository.MaintenanceBlock;
import com.mfg.ims.scheduling.SchedulingRepository.OpRow;
import com.mfg.ims.scheduling.SchedulingRepository.OperatorRow;
import com.mfg.ims.scheduling.SchedulingRepository.SkillRow;
import com.mfg.ims.scheduling.engine.ScheduleEngine;
import com.mfg.ims.scheduling.engine.ScheduleEngine.Interval;
import com.mfg.ims.scheduling.engine.ScheduleEngine.MachineSlot;
import com.mfg.ims.scheduling.engine.ScheduleEngine.OpRequest;
import com.mfg.ims.scheduling.engine.ScheduleEngine.OperatorSlot;
import com.mfg.ims.scheduling.engine.ScheduleEngine.Placement;
import com.mfg.ims.scheduling.engine.ScheduleEngine.Plan;

/**
 * Plans routing operations onto machines and operators, and lets a planner move one by hand. A machine, once an
 * operation has one (from its routing, a previous run or a move), is kept: the scheduler only chooses a machine for
 * operations that have none. Maintenance requests that name a date block that machine for the whole day.
 */
@Service
public class SchedulingService {

    public static final String MODE_FULL = "FULL";
    public static final String MODE_ONLY_UNSCHEDULED = "ONLY_UNSCHEDULED";
    static final int MAX_GANTT_DAYS = 62;

    private final SchedulingRepository repo;
    private final ProductionOperationRepository opRepo;
    private final MachineRepository machines;
    private final ProductionCalendar calendar;
    private final SystemSettingService settings;
    private final AuditService auditService;

    public SchedulingService(SchedulingRepository repo, ProductionOperationRepository opRepo,
                             MachineRepository machines, ProductionCalendar calendar, SystemSettingService settings,
                             AuditService auditService) {
        this.repo = repo;
        this.opRepo = opRepo;
        this.machines = machines;
        this.calendar = calendar;
        this.settings = settings;
        this.auditService = auditService;
    }

    /** Plans the pending operations; with {@code apply} the windows, machines and operators are saved. */
    @Transactional
    public SchedulePlanDto run(RunScheduleRequest req, boolean apply) {
        String mode = parseMode(req == null ? null : req.mode());
        List<Long> orderFilter = req == null || req.productionOrderIds() == null ? List.of()
                : req.productionOrderIds();
        ZoneId zone = calendar.zone();
        Instant now = Instant.now();
        // plans start on a whole minute: the windows and durations are whole minutes, so the schedule stays tidy
        Instant planFrom = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plus(Duration.ofMinutes(1));
        LocalDate today = now.atZone(zone).toLocalDate();
        int horizon = req != null && req.horizonDays() != null ? req.horizonDays()
                : Math.min(Math.max(settings.getInt("mfg.schedule.default_horizon_days", 14), 1), 90);
        LocalDate horizonEnd = today.plusDays(horizon);

        List<OpRow> all = repo.operations(List.of());
        Set<Long> orderScope = new HashSet<>(orderFilter);
        List<OpRow> scope = all.stream()
                .filter(o -> "PENDING".equals(o.status()))
                .filter(o -> orderScope.isEmpty() || orderScope.contains(o.orderId()))
                .filter(o -> MODE_FULL.equals(mode) || o.scheduledStart() == null)
                .toList();
        Set<Long> scopeIds = scope.stream().map(OpRow::opId).collect(Collectors.toSet());

        List<Machine> active = activeMachines();
        Map<Long, String> machineCodes = active.stream().collect(Collectors.toMap(Machine::getId,
                Machine::getMachineCode));
        List<MachineSlot> slots = active.stream()
                .map(m -> new MachineSlot(m.getId(), m.getMachineCode(), m.getMachineType())).toList();

        Map<Long, List<Interval>> machineBusy = new HashMap<>();
        Map<Long, List<Interval>> operatorBusy = new HashMap<>();
        for (OpRow o : all) {
            if (scopeIds.contains(o.opId()) || o.scheduledStart() == null || o.scheduledEnd() == null
                    || "COMPLETED".equals(o.status()) || !o.scheduledEnd().isAfter(now)) {
                continue;
            }
            Interval held = new Interval(o.scheduledStart().isAfter(now) ? o.scheduledStart() : now, o.scheduledEnd());
            if (o.machineId() != null) {
                machineBusy.computeIfAbsent(o.machineId(), k -> new ArrayList<>()).add(held);
            }
            if (o.operatorId() != null) {
                operatorBusy.computeIfAbsent(o.operatorId(), k -> new ArrayList<>()).add(held);
            }
        }
        for (MaintenanceBlock block : repo.maintenance(today, horizonEnd)) {
            machineBusy.computeIfAbsent(block.machineId(), k -> new ArrayList<>()).add(wholeDay(block.date(), zone));
        }

        List<Interval> windows = windows(today.minusDays(1), horizonEnd);
        Map<Long, List<OpRow>> byOrder = all.stream().collect(Collectors.groupingBy(OpRow::orderId,
                LinkedHashMap::new, Collectors.toList()));
        List<OpRequest> requests = new ArrayList<>();
        for (OpRow o : scope) {
            requests.add(new OpRequest(o.opId(), o.orderId(), o.orderNumber(), o.priority(), o.dueDate(),
                    o.sequenceNo(), o.operationMasterId(), o.defaultMachineType(), o.machineId(),
                    o.durationMinutes(), readyAfterPredecessors(o, byOrder.get(o.orderId()), scopeIds, now)));
        }

        Plan plan = ScheduleEngine.schedule(requests, slots, windows, machineBusy, operatorSlots(today, horizonEnd),
                operatorBusy, planFrom, zone);

        Map<Long, OpRow> rowsById = all.stream().collect(Collectors.toMap(OpRow::opId, Function.identity()));
        SchedulePlanDto dto = toDto(plan, rowsById, machineCodes, mode, planFrom, horizonEnd, apply);
        if (apply) {
            persist(plan, scope, mode);
        }
        return dto;
    }

    /** Operations in a window, grouped by machine, with maintenance days, working days and what is still unplanned. */
    @Transactional(readOnly = true)
    public GanttDto gantt(LocalDate from, LocalDate to, Long machineId) {
        ZoneId zone = calendar.zone();
        LocalDate today = Instant.now().atZone(zone).toLocalDate();
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : start.plusDays(13);
        if (end.isBefore(start)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The end " + end + " is before the start "
                    + start);
        }
        if (Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays() > MAX_GANTT_DAYS) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A schedule view may span at most " + MAX_GANTT_DAYS + " days");
        }
        if (machineId != null && !machines.existsById(machineId)) {
            throw new NotFoundException("Machine not found: " + machineId);
        }
        Instant fromTs = start.atStartOfDay(zone).toInstant();
        Instant toTs = end.plusDays(1).atStartOfDay(zone).toInstant();
        Map<Long, Instant> orderEnds = repo.scheduledEndByOrder();
        Map<Long, List<OpRow>> byMachine = repo.inWindow(fromTs, toTs, machineId == null ? 0 : machineId).stream()
                .filter(o -> o.machineId() != null)
                .collect(Collectors.groupingBy(OpRow::machineId));
        Map<Long, List<MaintenanceBlock>> maintenance = repo.maintenance(start, end).stream()
                .collect(Collectors.groupingBy(MaintenanceBlock::machineId));

        List<GanttMachineDto> rows = new ArrayList<>();
        for (Machine m : activeMachines()) {
            if (machineId != null && !machineId.equals(m.getId())) {
                continue;
            }
            List<GanttBarDto> bars = byMachine.getOrDefault(m.getId(), List.of()).stream()
                    .map(o -> bar(o, orderEnds, zone)).toList();
            List<GanttMaintenanceDto> blocks = maintenance.getOrDefault(m.getId(), List.of()).stream()
                    .map(b -> new GanttMaintenanceDto(b.requestId(), b.requestNumber(), b.date(), b.requestType()))
                    .toList();
            rows.add(new GanttMachineDto(m.getId(), m.getMachineCode(), m.getName(), bars, blocks));
        }
        List<LocalDate> workingDays = new ArrayList<>();
        List<GanttDayDto> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            boolean working = calendar.isWorkingDay(d);
            if (working) {
                workingDays.add(d);
            }
            days.add(new GanttDayDto(d, d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant(),
                    working));
        }
        List<UnscheduledOperationDto> unscheduled = repo.unscheduled().stream()
                .map(o -> new UnscheduledOperationDto(o.opId(), o.orderId(), o.orderNumber(), o.priority(),
                        o.sequenceNo(), o.operationName(), o.machineId()))
                .toList();
        return new GanttDto(start, end, zone.getId(), days, workingDays, rows, unscheduled);
    }

    /**
     * Moves one pending operation to a machine and start time. The start must not be in the past, must not precede the
     * end of the operation before it, and must not fall on another job or a maintenance day; a start outside working
     * time is moved to the next working time and reported. Problems that need a person's judgement (the next operation
     * now overlaps, the operator is not skilled or is away) come back as warnings.
     */
    @Transactional
    public RescheduleResultDto reschedule(long operationId, RescheduleRequest req) {
        OpRow op = repo.operation(operationId)
                .orElseThrow(() -> new NotFoundException("ProductionOperation", operationId));
        if (!"PENDING".equals(op.status())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation " + op.orderNumber() + "/" + op.sequenceNo() + " is " + op.status()
                            + " and can no longer be moved");
        }
        if (!List.of("RELEASED", "IN_PROGRESS").contains(op.orderStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Order " + op.orderNumber() + " is " + op.orderStatus() + " and cannot be scheduled");
        }
        Machine machine = machines.findById(req.machineId())
                .orElseThrow(() -> new NotFoundException("Machine not found: " + req.machineId()));
        if (!machine.isActive()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Machine " + machine.getMachineCode() + " is inactive");
        }
        ZoneId zone = calendar.zone();
        Instant now = Instant.now();
        if (req.start().isBefore(now.minus(Duration.ofMinutes(1)))) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An operation cannot be scheduled in the past");
        }
        List<OpRow> routing = repo.operationsOfOrder(op.orderId());
        Instant readyAt = null;
        for (OpRow p : routing) {
            if (p.sequenceNo() < op.sequenceNo()) {
                Instant end = "COMPLETED".equals(p.status()) ? p.completedAt() : p.scheduledEnd();
                if (end != null && (readyAt == null || end.isAfter(readyAt))) {
                    readyAt = end;
                }
            }
        }
        if (readyAt != null && req.start().isBefore(readyAt)) {
            throw new BusinessRuleException(ErrorCode.SCHEDULE_CONFLICT, "The operation would start at " + req.start()
                    + ", before the operation preceding it in the routing ends at " + readyAt);
        }

        LocalDate startDay = req.start().atZone(zone).toLocalDate();
        List<Interval> busy = new ArrayList<>();
        for (OpRow other : repo.scheduledOnMachine(machine.getId(), operationId)) {
            if (other.scheduledEnd() != null && other.scheduledEnd().isAfter(now)) {
                busy.add(new Interval(other.scheduledStart(), other.scheduledEnd()));
            }
        }
        for (MaintenanceBlock block : repo.maintenance(startDay.minusDays(1), startDay.plusDays(60))) {
            if (block.machineId() == machine.getId()) {
                busy.add(wholeDay(block.date(), zone));
            }
        }
        busy.sort(Comparator.comparing(Interval::start));
        Interval probe = new Interval(req.start(), req.start().plusSeconds(60));
        if (busy.stream().anyMatch(b -> b.start().isBefore(probe.end()) && probe.start().isBefore(b.end()))) {
            throw new BusinessRuleException(ErrorCode.SCHEDULE_CONFLICT,
                    "Machine " + machine.getMachineCode() + " is already taken at " + req.start()
                            + " by another operation or by maintenance");
        }
        Optional<List<Interval>> placed = ScheduleEngine.place(windows(startDay.minusDays(1), startDay.plusDays(60)),
                busy, req.start(), Math.max(op.durationMinutes(), 1));
        if (placed.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.SCHEDULE_CONFLICT,
                    "There is not enough working time on " + machine.getMachineCode() + " within 60 days of "
                            + req.start());
        }
        Interval span = new Interval(placed.get().get(0).start(), placed.get().get(placed.get().size() - 1).end());

        List<String> warnings = new ArrayList<>();
        if (!span.start().equals(req.start())) {
            warnings.add("SNAPPED_TO_WORKING_TIME");
        }
        if (op.defaultMachineType() != null && machine.getMachineType() != null
                && !op.defaultMachineType().equals(machine.getMachineType())) {
            warnings.add("MACHINE_TYPE_DIFFERS_FROM_OPERATION");
        }
        for (OpRow next : routing) {
            if (next.sequenceNo() > op.sequenceNo() && next.scheduledStart() != null
                    && next.scheduledStart().isBefore(span.end()) && "PENDING".equals(next.status())) {
                warnings.add("NEXT_OPERATION_STARTS_BEFORE_THIS_ENDS");
                break;
            }
        }
        if (op.operatorId() != null) {
            warnings.addAll(operatorWarnings(op, machine.getId(), span, zone));
        }

        ProductionOperation entity = opRepo.findById(operationId)
                .orElseThrow(() -> new NotFoundException("ProductionOperation", operationId));
        String before = describe(entity.getMachineId(), entity.getScheduledStart(), entity.getScheduledEnd());
        entity.setMachineId(machine.getId());
        entity.setScheduledStart(span.start());
        entity.setScheduledEnd(span.end());
        // flushed so the read below, which uses SQL, sees the new window
        opRepo.saveAndFlush(entity);
        auditService.record("SCHEDULE_MOVE", "ProductionOperation", String.valueOf(operationId), before,
                describe(machine.getId(), span.start(), span.end()), null);

        OpRow moved = repo.operation(operationId).orElseThrow();
        Map<Long, Instant> orderEnds = repo.scheduledEndByOrder();
        return new RescheduleResultDto(bar(moved, orderEnds, zone), machine.getId(), machine.getMachineCode(),
                List.copyOf(warnings));
    }

    /** Removes an operation's window (and nothing else); it then shows as unscheduled. */
    @Transactional
    public void unschedule(long operationId) {
        ProductionOperation op = opRepo.findById(operationId)
                .orElseThrow(() -> new NotFoundException("ProductionOperation", operationId));
        if (!"PENDING".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a pending operation can be unscheduled; this one is " + op.getStatus());
        }
        String before = describe(op.getMachineId(), op.getScheduledStart(), op.getScheduledEnd());
        op.setScheduledStart(null);
        op.setScheduledEnd(null);
        opRepo.save(op);
        auditService.record("SCHEDULE_CLEAR", "ProductionOperation", String.valueOf(operationId), before, "unscheduled",
                null);
    }

    private SchedulePlanDto toDto(Plan plan, Map<Long, OpRow> rows, Map<Long, String> machineCodes, String mode,
                                  Instant now, LocalDate horizonEnd, boolean applied) {
        List<PlacementDto> placements = new ArrayList<>();
        for (Placement p : plan.placements()) {
            OpRow o = rows.get(p.operationId());
            placements.add(new PlacementDto(p.operationId(), p.orderId(), o.orderNumber(), o.sequenceNo(),
                    o.operationName(), p.machineId(), machineCodes.get(p.machineId()), p.span().start(),
                    p.span().end(), p.operatorUserId(), p.warnings().stream().map(Enum::name).toList()));
        }
        List<UnplacedDto> unplaced = plan.unplaced().stream().map(u -> {
            OpRow o = rows.get(u.operationId());
            return new UnplacedDto(u.operationId(), u.orderId(), o.orderNumber(), o.sequenceNo(), o.operationName(),
                    u.reason().name());
        }).toList();
        List<OrderOutcomeDto> orders = plan.orders().stream().map(oc -> {
            OpRow any = rows.values().stream().filter(r -> r.orderId() == oc.orderId()).findFirst().orElseThrow();
            return new OrderOutcomeDto(oc.orderId(), any.orderNumber(), any.dueDate(), oc.endsAt(), oc.late());
        }).toList();
        return new SchedulePlanDto(applied, mode, now, horizonEnd, placements, unplaced, orders);
    }

    private void persist(Plan plan, List<OpRow> scope, String mode) {
        int placed = 0;
        for (Placement p : plan.placements()) {
            ProductionOperation op = opRepo.findById(p.operationId()).orElseThrow();
            if (op.getMachineId() == null) {
                op.setMachineId(p.machineId());
            }
            op.setScheduledStart(p.span().start());
            op.setScheduledEnd(p.span().end());
            if (p.operatorUserId() != null) {
                op.setAssignedOperatorId(p.operatorUserId());
            }
            opRepo.save(op);
            placed++;
        }
        int dropped = 0;
        if (MODE_FULL.equals(mode)) {
            Set<Long> placedIds = plan.placements().stream().map(Placement::operationId).collect(Collectors.toSet());
            for (OpRow o : scope) {
                if (!placedIds.contains(o.opId()) && o.scheduledStart() != null) {
                    ProductionOperation op = opRepo.findById(o.opId()).orElseThrow();
                    op.setScheduledStart(null);
                    op.setScheduledEnd(null);
                    opRepo.save(op);
                    dropped++;
                }
            }
        }
        auditService.record("SCHEDULE_RUN", "ProductionSchedule", mode, null,
                placed + " placed, " + plan.unplaced().size() + " unplaced, " + dropped + " windows dropped", null);
    }

    private Instant readyAfterPredecessors(OpRow op, List<OpRow> routing, Set<Long> scopeIds, Instant now) {
        Instant ready = null;
        for (OpRow p : routing) {
            if (p.sequenceNo() >= op.sequenceNo() || scopeIds.contains(p.opId())) {
                continue;
            }
            Instant end = switch (p.status()) {
                case "COMPLETED" -> p.completedAt();
                case "IN_PROGRESS" -> p.scheduledEnd() != null && p.scheduledEnd().isAfter(now) ? p.scheduledEnd() : now;
                default -> p.scheduledEnd();
            };
            if (end != null && (ready == null || end.isAfter(ready))) {
                ready = end;
            }
        }
        return ready;
    }

    private List<OperatorSlot> operatorSlots(LocalDate from, LocalDate to) {
        Map<Long, List<SkillRow>> skills = SchedulingRepository.byEmployee(repo.skills(from));
        Map<Long, Set<LocalDate>> away = repo.unavailableDays(from, to);
        List<OperatorSlot> out = new ArrayList<>();
        for (OperatorRow e : repo.operators(from, to)) {
            Map<Long, Integer> machineSkill = new HashMap<>();
            Map<Long, Integer> operationSkill = new HashMap<>();
            for (SkillRow s : skills.getOrDefault(e.employeeId(), List.of())) {
                if (s.machineId() != null) {
                    machineSkill.merge(s.machineId(), s.proficiency(), Math::max);
                }
                if (s.operationId() != null) {
                    operationSkill.merge(s.operationId(), s.proficiency(), Math::max);
                }
            }
            out.add(new OperatorSlot(e.userId(), machineSkill, operationSkill,
                    away.getOrDefault(e.employeeId(), Set.of())));
        }
        return out;
    }

    private List<String> operatorWarnings(OpRow op, long machineId, Interval span, ZoneId zone) {
        List<String> warnings = new ArrayList<>();
        LocalDate first = span.start().atZone(zone).toLocalDate();
        LocalDate last = span.end().minusSeconds(1).atZone(zone).toLocalDate();
        Optional<OperatorSlot> operator = operatorSlots(first, last).stream()
                .filter(o -> o.userId() == op.operatorId()).findFirst();
        if (operator.isEmpty()) {
            warnings.add("OPERATOR_NOT_AN_ACTIVE_EMPLOYEE");
            return warnings;
        }
        OperatorSlot o = operator.get();
        if (!o.machineProficiency().containsKey(machineId) && !o.operationProficiency()
                .containsKey(op.operationMasterId())) {
            warnings.add("OPERATOR_NOT_SKILLED");
        }
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            if (o.unavailableDates().contains(d)) {
                warnings.add("OPERATOR_UNAVAILABLE");
                break;
            }
        }
        return warnings;
    }

    private GanttBarDto bar(OpRow o, Map<Long, Instant> orderEnds, ZoneId zone) {
        Instant end = orderEnds.get(o.orderId());
        boolean late = end != null && o.dueDate() != null && end.atZone(zone).toLocalDate().isAfter(o.dueDate());
        return new GanttBarDto(o.opId(), o.orderId(), o.orderNumber(), o.productCode(), o.priority(), o.sequenceNo(),
                o.operationName(), o.status(), o.scheduledStart(), o.scheduledEnd(), o.operatorId(), o.operatorName(),
                late, !"PENDING".equals(o.status()));
    }

    private List<Interval> windows(LocalDate from, LocalDate to) {
        List<Interval> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            calendar.windowsOn(d).forEach(w -> out.add(new Interval(w.start(), w.end())));
        }
        out.sort(Comparator.comparing(Interval::start));
        return out;
    }

    private static Interval wholeDay(LocalDate date, ZoneId zone) {
        return new Interval(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private List<Machine> activeMachines() {
        return machines.findAllByActiveTrue().stream().sorted(Comparator.comparing(Machine::getMachineCode)).toList();
    }

    private static String parseMode(String mode) {
        if (mode == null || mode.isBlank() || MODE_FULL.equalsIgnoreCase(mode.trim())) {
            return MODE_FULL;
        }
        if (MODE_ONLY_UNSCHEDULED.equalsIgnoreCase(mode.trim())) {
            return MODE_ONLY_UNSCHEDULED;
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "Unknown scheduling mode: " + mode + " (expected FULL or ONLY_UNSCHEDULED)");
    }

    private static String describe(Long machineId, Instant start, Instant end) {
        return "machine " + machineId + " " + start + " .. " + end;
    }
}
