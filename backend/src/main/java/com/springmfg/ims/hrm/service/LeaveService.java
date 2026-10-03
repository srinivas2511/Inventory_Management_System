package com.springmfg.ims.hrm.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.hrm.domain.Attendance;
import com.springmfg.ims.hrm.domain.AttendanceStatus;
import com.springmfg.ims.hrm.domain.Employee;
import com.springmfg.ims.hrm.domain.LeaveApplication;
import com.springmfg.ims.hrm.domain.LeaveBalance;
import com.springmfg.ims.hrm.domain.LeaveStatus;
import com.springmfg.ims.hrm.domain.LeaveType;
import com.springmfg.ims.hrm.dto.LeaveDtos.ApplyLeaveRequest;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveApplicationDto;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveBalanceDto;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveTypeDto;
import com.springmfg.ims.hrm.repository.AttendanceRepository;
import com.springmfg.ims.hrm.repository.EmployeeRepository;
import com.springmfg.ims.hrm.repository.LeaveApplicationRepository;
import com.springmfg.ims.hrm.repository.LeaveBalanceRepository;
import com.springmfg.ims.hrm.repository.LeaveTypeRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * Leave types, per-employee balances and the application workflow (HRM-003). A leave year is the calendar year of
 * the application's first day; unlimited and unpaid types carry no balance.
 */
@Service
public class LeaveService {

    private final LeaveApplicationRepository repo;
    private final LeaveTypeRepository typeRepo;
    private final LeaveBalanceRepository balanceRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final AttendanceService attendanceService;
    private final HrmCalendar calendar;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public LeaveService(LeaveApplicationRepository repo, LeaveTypeRepository typeRepo,
                        LeaveBalanceRepository balanceRepo, EmployeeRepository employeeRepo,
                        AttendanceRepository attendanceRepo, AttendanceService attendanceService,
                        HrmCalendar calendar, DocumentNumberService numberService, AuditService auditService) {
        this.repo = repo;
        this.typeRepo = typeRepo;
        this.balanceRepo = balanceRepo;
        this.employeeRepo = employeeRepo;
        this.attendanceRepo = attendanceRepo;
        this.attendanceService = attendanceService;
        this.calendar = calendar;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeDto> types() {
        return typeRepo.findByActiveTrueOrderByLeaveCodeAsc().stream()
                .map(t -> new LeaveTypeDto(t.getId(), t.getLeaveCode(), t.getName(),
                        t.getMaxDaysPerYear() == null ? null : t.getMaxDaysPerYear().intValue(), t.isCarryForward(),
                        t.getMaxCarryForwardDays(), t.isPaid(), t.isActive()))
                .toList();
    }

    /** Balances of the tracked leave types; creates the year's rows (with carry-forward) on first use. */
    @Transactional
    public List<LeaveBalanceDto> balances(Long employeeId, Integer year) {
        Employee e = resolveViewedEmployee(employeeId);
        int y = year != null ? year : LocalDate.now(calendar.zone()).getYear();
        List<LeaveBalanceDto> out = new ArrayList<>();
        for (LeaveType t : typeRepo.findByActiveTrueOrderByLeaveCodeAsc()) {
            if (!tracksBalance(t)) {
                continue;
            }
            LeaveBalance b = balanceRow(e.getId(), t, y);
            out.add(new LeaveBalanceDto(t.getId(), t.getLeaveCode(), t.getName(), y, t.isPaid(), b.getEntitled(),
                    b.getCarriedForward(), b.getUsed(), b.available()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplicationDto> search(String status, Long employeeId, LocalDate from, LocalDate to,
                                            Pageable pageable) {
        boolean seeAll = HrmSecurity.has("HR_VIEW") || HrmSecurity.has("LEAVE_APPROVE");
        Long ownEmployeeId = null;
        if (!seeAll) {
            Long uid = HrmSecurity.currentUserId();
            ownEmployeeId = uid == null ? null : employeeRepo.findByUserId(uid).map(Employee::getId).orElse(null);
            if (ownEmployeeId == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
        }
        Long restrictTo = seeAll ? employeeId : ownEmployeeId;
        Specification<LeaveApplication> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null && !status.isBlank()) p.add(cb.equal(root.get("status"), status.trim()));
            if (restrictTo != null) p.add(cb.equal(root.get("employeeId"), restrictTo));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("toDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("fromDate"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<LeaveApplication> page = repo.findAll(spec, pageable);
        Context ctx = context(page.getContent());
        return page.map(a -> toDto(a, ctx));
    }

    @Transactional(readOnly = true)
    public LeaveApplicationDto findById(Long id) {
        LeaveApplication a = require(id);
        requireMayView(a);
        return toDto(a, context(List.of(a)));
    }

    @Transactional
    public LeaveApplicationDto apply(ApplyLeaveRequest req) {
        Employee e = resolveApplicant(req.employeeId());
        LeaveType type = typeRepo.findById(req.leaveTypeId())
                .filter(LeaveType::isActive)
                .orElseThrow(() -> new NotFoundException("LeaveType", req.leaveTypeId()));
        LocalDate from = req.fromDate();
        LocalDate to = req.toDate();
        if (to.isBefore(from)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "End date is before the start date");
        }
        if (from.isBefore(e.getDateOfJoining())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Leave starts before the joining date");
        }
        if (!e.isActive()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Employee is not active");
        }
        BigDecimal days = daysFor(from, to, req.halfDay());
        requireUnlocked(from, to);
        if (!repo.findOpenOverlapping(e.getId(), from, to).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.LEAVE_OVERLAP,
                    "The employee already has a pending or approved leave overlapping these dates");
        }
        if (tracksBalance(type)) {
            requireBalance(e.getId(), type, from.getYear(), days);
        }

        LeaveApplication a = new LeaveApplication();
        a.setApplicationNumber(numberService.next("LV"));
        a.setEmployeeId(e.getId());
        a.setLeaveTypeId(type.getId());
        a.setFromDate(from);
        a.setToDate(to);
        a.setHalfDay(req.halfDay());
        a.setDays(days);
        a.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        a = repo.save(a);
        auditService.record("LEAVE_APPLY", "LEAVE", String.valueOf(a.getId()), null,
                a.getApplicationNumber() + " " + type.getLeaveCode() + " " + days + "d", a.getReason());
        return toDto(a, context(List.of(a)));
    }

    @Transactional
    public LeaveApplicationDto approve(Long id, String reason) {
        LeaveApplication a = require(id);
        Employee e = employee(a.getEmployeeId());
        requireNotOwn(e, "approve");
        LeaveStatus.valueOf(a.getStatus()).transitionTo(LeaveStatus.APPROVED);
        requireUnlocked(a.getFromDate(), a.getToDate());
        LeaveType type = typeRepo.findById(a.getLeaveTypeId()).orElseThrow();

        if (tracksBalance(type)) {
            LeaveBalance b = requireBalance(e.getId(), type, a.getFromDate().getYear(), a.getDays());
            b.setUsed(b.getUsed().add(a.getDays()));
            balanceRepo.save(b);
        }
        if (!a.isHalfDay()) {
            markLeaveDays(a, e);
        }
        return decide(a, LeaveStatus.APPROVED, "LEAVE_APPROVE", reason);
    }

    @Transactional
    public LeaveApplicationDto reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required to reject leave");
        }
        LeaveApplication a = require(id);
        requireNotOwn(employee(a.getEmployeeId()), "reject");
        LeaveStatus.valueOf(a.getStatus()).transitionTo(LeaveStatus.REJECTED);
        return decide(a, LeaveStatus.REJECTED, "LEAVE_REJECT", reason);
    }

    /** The applicant may withdraw a pending application; LEAVE_APPROVE holders may also cancel approved leave. */
    @Transactional
    public LeaveApplicationDto cancel(Long id, String reason) {
        LeaveApplication a = require(id);
        Employee e = employee(a.getEmployeeId());
        LeaveStatus current = LeaveStatus.valueOf(a.getStatus());
        current.transitionTo(LeaveStatus.CANCELLED);
        boolean approver = HrmSecurity.has("LEAVE_APPROVE");
        boolean own = isOwn(e);
        if (current == LeaveStatus.PENDING && !own && !approver) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "Only the applicant or an approver can cancel this leave");
        }
        if (current == LeaveStatus.APPROVED) {
            if (!approver) {
                throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "Only an approver can cancel approved leave");
            }
            requireUnlocked(a.getFromDate(), a.getToDate());
            LeaveType type = typeRepo.findById(a.getLeaveTypeId()).orElseThrow();
            if (tracksBalance(type)) {
                LeaveBalance b = balanceRow(e.getId(), type, a.getFromDate().getYear());
                b.setUsed(b.getUsed().subtract(a.getDays()).max(BigDecimal.ZERO));
                balanceRepo.save(b);
            }
            attendanceRepo.deleteByEmployeeIdAndStatusAndRemarks(e.getId(), AttendanceStatus.LEAVE.name(),
                    leaveTag(a));
        }
        return decide(a, LeaveStatus.CANCELLED, "LEAVE_CANCEL", reason);
    }

    // ---- internals ----------------------------------------------------------------------------------------

    private LeaveApplicationDto decide(LeaveApplication a, LeaveStatus target, String action, String reason) {
        String old = a.getStatus();
        a.setStatus(target.name());
        a.setDecidedBy(HrmSecurity.currentUserId());
        a.setDecidedAt(Instant.now());
        a.setDecisionReason(reason == null || reason.isBlank() ? null : reason.trim());
        a = repo.save(a);
        auditService.record(action, "LEAVE", String.valueOf(a.getId()), old, target.name(), a.getDecisionReason());
        return toDto(a, context(List.of(a)));
    }

    /** Working days in the range get a LEAVE attendance row, unless HR has already recorded something for the day. */
    private void markLeaveDays(LeaveApplication a, Employee e) {
        Set<DayOfWeek> off = calendar.weeklyOff();
        for (LocalDate d = a.getFromDate(); !d.isAfter(a.getToDate()); d = d.plusDays(1)) {
            if (!HrmCalendar.isWorkingDay(d, off)
                    || attendanceRepo.findByEmployeeIdAndAttendanceDate(e.getId(), d).isPresent()) {
                continue;
            }
            Attendance row = new Attendance();
            row.setEmployeeId(e.getId());
            row.setAttendanceDate(d);
            row.setStatus(AttendanceStatus.LEAVE.name());
            row.setSource(Attendance.SOURCE_AUTO);
            row.setRemarks(leaveTag(a));
            attendanceRepo.save(row);
        }
    }

    private static String leaveTag(LeaveApplication a) {
        return "LEAVE " + a.getApplicationNumber();
    }

    private BigDecimal daysFor(LocalDate from, LocalDate to, boolean halfDay) {
        if (halfDay) {
            if (!from.equals(to)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A half-day leave covers a single date");
            }
            if (!HrmCalendar.isWorkingDay(from, calendar.weeklyOff())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "That date is a weekly off");
            }
            return new BigDecimal("0.5");
        }
        int working = calendar.countWorkingDays(from, to);
        if (working == 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The dates contain no working days");
        }
        return new BigDecimal(working).setScale(1);
    }

    private void requireUnlocked(LocalDate from, LocalDate to) {
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
            attendanceService.requireUnlocked(ym.atDay(1));
        }
    }

    private static boolean tracksBalance(LeaveType t) {
        return t.isPaid() && t.getMaxDaysPerYear() != null;
    }

    private LeaveBalance requireBalance(Long employeeId, LeaveType type, int year, BigDecimal days) {
        LeaveBalance b = balanceRow(employeeId, type, year);
        if (b.available().compareTo(days) < 0) {
            throw new BusinessRuleException(ErrorCode.LEAVE_BALANCE_INSUFFICIENT,
                    type.getLeaveCode() + " balance is " + b.available() + " day(s); " + days + " requested");
        }
        return b;
    }

    /** The employee's balance row for the year; first use seeds the entitlement and the capped carry-forward. */
    private LeaveBalance balanceRow(Long employeeId, LeaveType type, int year) {
        return balanceRepo.findByEmployeeIdAndLeaveTypeIdAndBalanceYear(employeeId, type.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployeeId(employeeId);
                    b.setLeaveTypeId(type.getId());
                    b.setBalanceYear(year);
                    b.setEntitled(BigDecimal.valueOf(type.getMaxDaysPerYear()).setScale(1));
                    if (type.isCarryForward()) {
                        BigDecimal previous = balanceRepo
                                .findByEmployeeIdAndLeaveTypeIdAndBalanceYear(employeeId, type.getId(), year - 1)
                                .map(LeaveBalance::available).orElse(BigDecimal.ZERO);
                        b.setCarriedForward(previous.max(BigDecimal.ZERO)
                                .min(BigDecimal.valueOf(type.getMaxCarryForwardDays())).setScale(1));
                    }
                    return balanceRepo.save(b);
                });
    }

    private Employee resolveApplicant(Long requestedEmployeeId) {
        Long uid = HrmSecurity.currentUserId();
        if (requestedEmployeeId != null) {
            Employee target = employee(requestedEmployeeId);
            if (!isOwn(target) && !HrmSecurity.has("ATTENDANCE_MANAGE")) {
                throw new BusinessRuleException(ErrorCode.ACCESS_DENIED,
                        "You can apply for leave only for yourself");
            }
            return target;
        }
        if (uid == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "employeeId is required");
        }
        return employeeRepo.findByUserId(uid).orElseThrow(
                () -> new NotFoundException("No employee record is linked to your user"));
    }

    private Employee resolveViewedEmployee(Long employeeId) {
        Long uid = HrmSecurity.currentUserId();
        if (employeeId == null) {
            if (uid == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "employeeId is required");
            }
            return employeeRepo.findByUserId(uid).orElseThrow(
                    () -> new NotFoundException("No employee record is linked to your user"));
        }
        Employee e = employee(employeeId);
        if (!isOwn(e) && !HrmSecurity.has("HR_VIEW") && !HrmSecurity.has("LEAVE_APPROVE")) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "You can view only your own leave balances");
        }
        return e;
    }

    private void requireMayView(LeaveApplication a) {
        if (HrmSecurity.has("HR_VIEW") || HrmSecurity.has("LEAVE_APPROVE")) {
            return;
        }
        if (!isOwn(employee(a.getEmployeeId()))) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "You can view only your own leave");
        }
    }

    private void requireNotOwn(Employee e, String verb) {
        if (isOwn(e)) {
            throw new BusinessRuleException(ErrorCode.SEGREGATION_OF_DUTIES, "You cannot " + verb + " your own leave");
        }
    }

    private static boolean isOwn(Employee e) {
        Long uid = HrmSecurity.currentUserId();
        return uid != null && uid.equals(e.getUserId());
    }

    private LeaveApplication require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("LeaveApplication", id));
    }

    private Employee employee(Long id) {
        return employeeRepo.findById(id).orElseThrow(() -> new NotFoundException("Employee", id));
    }

    private record Context(Map<Long, Employee> employees, Map<Long, LeaveType> types) {}

    private Context context(List<LeaveApplication> applications) {
        Map<Long, Employee> employees = employeeRepo
                .findAllById(applications.stream().map(LeaveApplication::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, LeaveType> types = typeRepo
                .findAllById(applications.stream().map(LeaveApplication::getLeaveTypeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        return new Context(employees, types);
    }

    private LeaveApplicationDto toDto(LeaveApplication a, Context ctx) {
        Employee e = ctx.employees().get(a.getEmployeeId());
        LeaveType t = ctx.types().get(a.getLeaveTypeId());
        return new LeaveApplicationDto(a.getId(), a.getApplicationNumber(), a.getEmployeeId(),
                e == null ? null : e.getEmployeeCode(), e == null ? null : e.fullName(), a.getLeaveTypeId(),
                t == null ? null : t.getLeaveCode(), a.getFromDate(), a.getToDate(), a.isHalfDay(), a.getDays(),
                a.getReason(), a.getStatus(), a.getDecidedBy(), a.getDecidedAt(), a.getDecisionReason(),
                a.getVersion(), allowedActions(a, e));
    }

    private static List<String> allowedActions(LeaveApplication a, Employee e) {
        List<String> actions = new ArrayList<>();
        boolean approver = HrmSecurity.has("LEAVE_APPROVE");
        boolean own = e != null && isOwn(e);
        switch (LeaveStatus.valueOf(a.getStatus())) {
            case PENDING -> {
                if (approver && !own) {
                    actions.add("APPROVE");
                    actions.add("REJECT");
                }
                if (own || approver) {
                    actions.add("CANCEL");
                }
            }
            case APPROVED -> {
                if (approver) {
                    actions.add("CANCEL");
                }
            }
            case REJECTED, CANCELLED -> { }
        }
        return actions;
    }
}
