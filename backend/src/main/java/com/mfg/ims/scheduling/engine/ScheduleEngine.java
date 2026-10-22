package com.mfg.ims.scheduling.engine;

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

/**
 * Finite-capacity forward scheduler for routing operations. It is deterministic and pure: no Spring, no database, so
 * it can be tested with numbers worked out by hand.
 *
 * <p>Operations are taken in the order priority (1 is the most urgent), due date (earliest first, none last), order id,
 * then routing sequence. Each operation is placed on the machine that lets it finish earliest, never before the
 * operation before it in its routing has finished, using only the working windows and the time the machine has free.
 * An operation may run across several windows (a day's end, a break in another job); it then occupies only the free
 * parts. After placement an operator is chosen: skilled for the machine or the operation, present on every day the
 * operation touches, and not already busy then; the most proficient wins, then the one with least work so far.
 */
public final class ScheduleEngine {

    private ScheduleEngine() {
    }

    /** A half-open interval [start, end). */
    public record Interval(Instant start, Instant end) {
        public long minutes() {
            return Duration.between(start, end).toMinutes();
        }

        boolean overlaps(Interval other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    public record MachineSlot(long machineId, String code, String type) {
    }

    /**
     * One operation to place. {@code machineId} fixes the machine; otherwise any machine of {@code machineType}
     * (or any machine, when the type is null) qualifies. {@code notBefore} is when its predecessor is done.
     */
    public record OpRequest(
            long operationId,
            long orderId,
            String orderNumber,
            int priority,
            LocalDate dueDate,
            int sequenceNo,
            long operationMasterId,
            String machineType,
            Long machineId,
            long durationMinutes,
            Instant notBefore) {
    }

    /** An operator and the skills the skill matrix gives them (machine or operation id to proficiency 1-5). */
    public record OperatorSlot(
            long userId,
            Map<Long, Integer> machineProficiency,
            Map<Long, Integer> operationProficiency,
            Set<LocalDate> unavailableDates) {

        int proficiencyFor(long machineId, long operationMasterId) {
            return Math.max(machineProficiency.getOrDefault(machineId, 0),
                    operationProficiency.getOrDefault(operationMasterId, 0));
        }

        boolean skilledFor(long machineId, long operationMasterId) {
            return machineProficiency.containsKey(machineId) || operationProficiency.containsKey(operationMasterId);
        }
    }

    public enum Warning {
        /** Nobody in the skill matrix is authorised for this machine or operation. */
        NO_SKILLED_OPERATOR,
        /** Skilled operators exist but each is absent or already busy at that time. */
        NO_AVAILABLE_OPERATOR
    }

    public enum UnplacedReason {
        NO_MACHINE,
        HORIZON_EXCEEDED,
        PREDECESSOR_UNPLACED
    }

    public record Placement(long operationId, long orderId, long machineId, Interval span, List<Interval> segments,
                            Long operatorUserId, List<Warning> warnings) {
    }

    public record Unplaced(long operationId, long orderId, UnplacedReason reason) {
    }

    /** When an order's last placed operation ends, and whether that is after its due date. */
    public record OrderOutcome(long orderId, Instant endsAt, boolean late) {
    }

    public record Plan(List<Placement> placements, List<Unplaced> unplaced, List<OrderOutcome> orders) {
    }

    /**
     * @param windows  the times machines may run, sorted and non-overlapping
     * @param busy     per machine, time that is already taken (running work, fixed jobs, maintenance)
     * @param operatorBusy per operator, time already taken
     * @param notBefore nothing is placed earlier than this (normally now)
     */
    public static Plan schedule(List<OpRequest> ops, List<MachineSlot> machines, List<Interval> windows,
                                Map<Long, List<Interval>> busy, List<OperatorSlot> operators,
                                Map<Long, List<Interval>> operatorBusy, Instant notBefore, ZoneId zone) {
        Map<Long, List<Interval>> machineBusy = copy(busy);
        Map<Long, List<Interval>> opBusy = copy(operatorBusy);
        Map<Long, Long> operatorLoad = new HashMap<>();
        Map<Long, Instant> orderEnd = new HashMap<>();
        Map<Long, LocalDate> orderDue = new HashMap<>();
        Set<Long> blockedOrders = new HashSet<>();

        List<OpRequest> sorted = new ArrayList<>(ops);
        sorted.sort(Comparator.comparingInt(OpRequest::priority)
                .thenComparing(OpRequest::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(OpRequest::orderId)
                .thenComparingInt(OpRequest::sequenceNo));

        List<Placement> placements = new ArrayList<>();
        List<Unplaced> unplaced = new ArrayList<>();
        for (OpRequest op : sorted) {
            orderDue.putIfAbsent(op.orderId(), op.dueDate());
            if (blockedOrders.contains(op.orderId())) {
                unplaced.add(new Unplaced(op.operationId(), op.orderId(), UnplacedReason.PREDECESSOR_UNPLACED));
                continue;
            }
            Instant earliest = latest(notBefore, op.notBefore(), orderEnd.get(op.orderId()));
            List<MachineSlot> candidates = candidates(op, machines);
            if (candidates.isEmpty()) {
                unplaced.add(new Unplaced(op.operationId(), op.orderId(), UnplacedReason.NO_MACHINE));
                blockedOrders.add(op.orderId());
                continue;
            }
            MachineSlot best = null;
            List<Interval> bestSegments = null;
            for (MachineSlot m : candidates) {
                Optional<List<Interval>> segments = place(windows, machineBusy.getOrDefault(m.machineId(), List.of()),
                        earliest, Math.max(op.durationMinutes(), 1));
                if (segments.isEmpty()) {
                    continue;
                }
                Instant end = last(segments.get());
                if (best == null || end.isBefore(last(bestSegments))
                        || (end.equals(last(bestSegments)) && m.code().compareTo(best.code()) < 0)) {
                    best = m;
                    bestSegments = segments.get();
                }
            }
            if (best == null) {
                unplaced.add(new Unplaced(op.operationId(), op.orderId(), UnplacedReason.HORIZON_EXCEEDED));
                blockedOrders.add(op.orderId());
                continue;
            }
            Interval span = new Interval(bestSegments.get(0).start(), last(bestSegments));
            insert(machineBusy.computeIfAbsent(best.machineId(), k -> new ArrayList<>()), span);
            orderEnd.merge(op.orderId(), span.end(), (a, b) -> a.isAfter(b) ? a : b);

            List<Warning> warnings = new ArrayList<>();
            Long operator = chooseOperator(op, best.machineId(), bestSegments, span, operators, opBusy, operatorLoad,
                    zone, warnings);
            placements.add(new Placement(op.operationId(), op.orderId(), best.machineId(), span, bestSegments, operator,
                    List.copyOf(warnings)));
        }

        Map<Long, Instant> ends = new LinkedHashMap<>();
        for (Placement p : placements) {
            ends.merge(p.orderId(), p.span().end(), (a, b) -> a.isAfter(b) ? a : b);
        }
        List<OrderOutcome> outcomes = new ArrayList<>();
        ends.forEach((orderId, end) -> {
            LocalDate due = orderDue.get(orderId);
            outcomes.add(new OrderOutcome(orderId, end, due != null && end.atZone(zone).toLocalDate().isAfter(due)));
        });
        return new Plan(placements, unplaced, outcomes);
    }

    /**
     * Lays {@code minutes} of work into the free parts of the windows from {@code earliest} on. Work may continue
     * over a gap in the working windows (overnight, a weekly off) but never over a busy interval: if another job sits
     * in between, the attempt restarts after it, so the spans of jobs on one machine never interleave. Empty when the
     * windows run out first. Segments are in time order.
     */
    public static Optional<List<Interval>> place(List<Interval> windows, List<Interval> busy, Instant earliest,
                                                 long minutes) {
        long remaining = minutes;
        List<Interval> segments = new ArrayList<>();
        for (Interval window : windows) {
            if (!window.end().isAfter(earliest)) {
                continue;
            }
            Interval usable = new Interval(window.start().isAfter(earliest) ? window.start() : earliest, window.end());
            for (Interval free : subtract(usable, busy)) {
                if (!segments.isEmpty()) {
                    Interval gap = new Interval(segments.get(segments.size() - 1).end(), free.start());
                    if (busy.stream().anyMatch(gap::overlaps)) {
                        segments.clear();
                        remaining = minutes;
                    }
                }
                long take = Math.min(remaining, free.minutes());
                if (take > 0) {
                    segments.add(new Interval(free.start(), free.start().plus(Duration.ofMinutes(take))));
                    remaining -= take;
                }
                if (remaining == 0) {
                    return Optional.of(segments);
                }
            }
        }
        return Optional.empty();
    }

    /** The parts of {@code window} not covered by any busy interval. */
    static List<Interval> subtract(Interval window, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        Instant cursor = window.start();
        for (Interval b : busy) {
            if (!b.end().isAfter(cursor)) {
                continue;
            }
            if (!b.start().isBefore(window.end())) {
                break;
            }
            if (b.start().isAfter(cursor)) {
                free.add(new Interval(cursor, b.start()));
            }
            if (b.end().isAfter(cursor)) {
                cursor = b.end();
            }
        }
        if (cursor.isBefore(window.end())) {
            free.add(new Interval(cursor, window.end()));
        }
        return free;
    }

    private static List<MachineSlot> candidates(OpRequest op, List<MachineSlot> machines) {
        if (op.machineId() != null) {
            return machines.stream().filter(m -> m.machineId() == op.machineId()).toList();
        }
        return machines.stream()
                .filter(m -> op.machineType() == null || op.machineType().equals(m.type()))
                .toList();
    }

    private static Long chooseOperator(OpRequest op, long machineId, List<Interval> segments, Interval span,
                                       List<OperatorSlot> operators, Map<Long, List<Interval>> opBusy,
                                       Map<Long, Long> load, ZoneId zone, List<Warning> warnings) {
        List<OperatorSlot> skilled = operators.stream()
                .filter(o -> o.skilledFor(machineId, op.operationMasterId())).toList();
        if (skilled.isEmpty()) {
            warnings.add(Warning.NO_SKILLED_OPERATOR);
            return null;
        }
        Set<LocalDate> days = new HashSet<>();
        for (LocalDate d = span.start().atZone(zone).toLocalDate();
             !d.isAfter(span.end().minusSeconds(1).atZone(zone).toLocalDate()); d = d.plusDays(1)) {
            days.add(d);
        }
        OperatorSlot best = null;
        for (OperatorSlot o : skilled) {
            if (days.stream().anyMatch(o.unavailableDates()::contains)) {
                continue;
            }
            List<Interval> taken = opBusy.getOrDefault(o.userId(), List.of());
            if (taken.stream().anyMatch(span::overlaps)) {
                continue;
            }
            if (best == null || better(o, best, machineId, op.operationMasterId(), load)) {
                best = o;
            }
        }
        if (best == null) {
            warnings.add(Warning.NO_AVAILABLE_OPERATOR);
            return null;
        }
        insert(opBusy.computeIfAbsent(best.userId(), k -> new ArrayList<>()), span);
        load.merge(best.userId(), span.minutes(), Long::sum);
        return best.userId();
    }

    private static boolean better(OperatorSlot a, OperatorSlot b, long machineId, long operationMasterId,
                                  Map<Long, Long> load) {
        int pa = a.proficiencyFor(machineId, operationMasterId);
        int pb = b.proficiencyFor(machineId, operationMasterId);
        if (pa != pb) {
            return pa > pb;
        }
        long la = load.getOrDefault(a.userId(), 0L);
        long lb = load.getOrDefault(b.userId(), 0L);
        if (la != lb) {
            return la < lb;
        }
        return a.userId() < b.userId();
    }

    private static void insert(List<Interval> sortedBusy, Interval added) {
        int i = 0;
        while (i < sortedBusy.size() && sortedBusy.get(i).start().isBefore(added.start())) {
            i++;
        }
        sortedBusy.add(i, added);
    }

    private static Map<Long, List<Interval>> copy(Map<Long, List<Interval>> source) {
        Map<Long, List<Interval>> out = new HashMap<>();
        source.forEach((k, v) -> {
            List<Interval> list = new ArrayList<>(v);
            list.sort(Comparator.comparing(Interval::start));
            out.put(k, list);
        });
        return out;
    }

    private static Instant last(List<Interval> segments) {
        return segments.get(segments.size() - 1).end();
    }

    private static Instant latest(Instant... instants) {
        Instant out = null;
        for (Instant i : instants) {
            if (i != null && (out == null || i.isAfter(out))) {
                out = i;
            }
        }
        return out;
    }
}
