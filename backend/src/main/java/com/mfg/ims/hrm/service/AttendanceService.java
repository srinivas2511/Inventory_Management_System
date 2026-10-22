package com.mfg.ims.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.hrm.domain.Attendance;
import com.mfg.ims.hrm.domain.AttendanceStatus;
import com.mfg.ims.hrm.domain.Employee;
import com.mfg.ims.hrm.domain.PayrollStatus;
import com.mfg.ims.hrm.domain.Shift;
import com.mfg.ims.hrm.dto.AttendanceDtos;
import com.mfg.ims.hrm.dto.AttendanceDtos.AttendanceDto;
import com.mfg.ims.hrm.dto.AttendanceDtos.AttendanceSummaryDto;
import com.mfg.ims.hrm.dto.AttendanceDtos.BulkAttendanceRequest;
import com.mfg.ims.hrm.dto.AttendanceDtos.RecordAttendanceRequest;
import com.mfg.ims.hrm.repository.AttendanceRepository;
import com.mfg.ims.hrm.repository.EmployeeRepository;
import com.mfg.ims.hrm.repository.PayrollRunRepository;
import com.mfg.ims.hrm.repository.ShiftRepository;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");

    private final AttendanceRepository repo;
    private final EmployeeRepository employeeRepo;
    private final ShiftRepository shiftRepo;
    private final PayrollRunRepository payrollRunRepo;
    private final HrmCalendar calendar;
    private final AuditService auditService;

    public AttendanceService(AttendanceRepository repo, EmployeeRepository employeeRepo, ShiftRepository shiftRepo,
                             PayrollRunRepository payrollRunRepo, HrmCalendar calendar, AuditService auditService) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
        this.shiftRepo = shiftRepo;
        this.payrollRunRepo = payrollRunRepo;
        this.calendar = calendar;
        this.auditService = auditService;
    }

    /** Everyone employed on the date, with their record or NOT_RECORDED. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> daily(LocalDate date) {
        Map<Long, Attendance> byEmployee = repo.findByAttendanceDate(date).stream()
                .collect(Collectors.toMap(Attendance::getEmployeeId, Function.identity()));
        Map<String, Shift> shifts = shiftsByCode();
        List<AttendanceDto> rows = new ArrayList<>();
        for (Employee e : employeeRepo.findEmployedDuring(date, date)) {
            Attendance a = byEmployee.get(e.getId());
            rows.add(a != null ? toDto(a, e, shifts) : new AttendanceDto(null, e.getId(), e.getEmployeeCode(),
                    e.fullName(), date, e.getDefaultShift(), null, null, AttendanceDtos.NOT_RECORDED, null, false,
                    null, null));
        }
        return rows;
    }

    @Transactional
    public AttendanceDto record(RecordAttendanceRequest req) {
        Employee e = employeeRepo.findById(req.employeeId())
                .orElseThrow(() -> new NotFoundException("Employee", req.employeeId()));
        LocalDate date = req.attendanceDate();
        validate(req, e);
        requireUnlocked(date);

        String shiftCode = req.shift() != null && !req.shift().isBlank() ? req.shift().trim() : e.getDefaultShift();
        if (shiftCode != null && shiftRepo.findByShiftCode(shiftCode).isEmpty()) {
            throw new NotFoundException("Shift", shiftCode);
        }

        Attendance a = repo.findByEmployeeIdAndAttendanceDate(e.getId(), date).orElse(null);
        String oldValue = null;
        if (a == null) {
            a = new Attendance();
            a.setEmployeeId(e.getId());
            a.setAttendanceDate(date);
        } else {
            oldValue = a.getStatus();
        }
        a.setShift(shiftCode);
        a.setCheckIn(req.checkIn());
        a.setCheckOut(req.checkOut());
        a.setStatus(req.status().name());
        a.setSource(Attendance.SOURCE_MANUAL);
        a.setRemarks(req.remarks() == null || req.remarks().isBlank() ? null : req.remarks().trim());
        a = repo.save(a);
        if (oldValue != null) {
            auditService.record("ATTENDANCE_CORRECT", "ATTENDANCE", String.valueOf(a.getId()), oldValue,
                    a.getStatus() + " " + e.getEmployeeCode() + " " + date, a.getRemarks());
        }
        return toDto(a, e, shiftsByCode());
    }

    @Transactional
    public List<AttendanceDto> recordBulk(BulkAttendanceRequest req) {
        List<AttendanceDto> out = new ArrayList<>();
        for (RecordAttendanceRequest entry : req.entries()) {
            out.add(record(entry));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AttendanceSummaryDto> summary(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<Long, List<Attendance>> byEmployee = repo.findByAttendanceDateBetween(from, to).stream()
                .collect(Collectors.groupingBy(Attendance::getEmployeeId));
        Map<String, Shift> shifts = shiftsByCode();
        List<AttendanceSummaryDto> rows = new ArrayList<>();
        for (Employee e : employeeRepo.findEmployedDuring(from, to)) {
            int present = 0;
            int absent = 0;
            int half = 0;
            int leave = 0;
            int holiday = 0;
            int late = 0;
            BigDecimal hours = BigDecimal.ZERO;
            for (Attendance a : byEmployee.getOrDefault(e.getId(), List.of())) {
                switch (AttendanceStatus.valueOf(a.getStatus())) {
                    case PRESENT -> present++;
                    case ABSENT -> absent++;
                    case HALF_DAY -> half++;
                    case LEAVE -> leave++;
                    case HOLIDAY -> holiday++;
                }
                AttendanceDto dto = toDto(a, e, shifts);
                if (dto.late()) {
                    late++;
                }
                if (dto.workHours() != null) {
                    hours = hours.add(dto.workHours());
                }
            }
            rows.add(new AttendanceSummaryDto(e.getId(), e.getEmployeeCode(), e.fullName(), present, absent, half,
                    leave, holiday, late, hours));
        }
        return rows;
    }

    /**
     * Records presence from production activity: the operator's first start of the day sets the check-in and
     * every completion pushes the check-out. Best effort and in its own transaction so a problem here can never
     * touch the production operation that triggered it. Days that are holidays, leave or half days are left to HR.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCapture(Long operatorUserId, Instant at, boolean completion) {
        if (operatorUserId == null || at == null) {
            return;
        }
        Employee e = employeeRepo.findByUserId(operatorUserId).filter(Employee::isActive).orElse(null);
        if (e == null) {
            return;
        }
        LocalDate day = calendar.toLocalDate(at);
        if (isLocked(day)) {
            log.info("Attendance auto-capture skipped for {} on {}: payroll for the month is approved",
                    e.getEmployeeCode(), day);
            return;
        }
        Attendance a = repo.findByEmployeeIdAndAttendanceDate(e.getId(), day).orElse(null);
        if (a == null) {
            a = new Attendance();
            a.setEmployeeId(e.getId());
            a.setAttendanceDate(day);
            a.setShift(e.getDefaultShift());
            a.setSource(Attendance.SOURCE_AUTO);
        } else if (a.getStatus().equals(AttendanceStatus.HOLIDAY.name())
                || a.getStatus().equals(AttendanceStatus.LEAVE.name())
                || a.getStatus().equals(AttendanceStatus.HALF_DAY.name())) {
            return;
        }
        a.setStatus(AttendanceStatus.PRESENT.name());
        if (a.getCheckIn() == null || at.isBefore(a.getCheckIn())) {
            a.setCheckIn(at);
        }
        if (completion && (a.getCheckOut() == null || at.isAfter(a.getCheckOut()))) {
            a.setCheckOut(at);
        }
        repo.save(a);
    }

    public boolean isLocked(LocalDate date) {
        return payrollRunRepo.existsByPeriodYearAndPeriodMonthAndStatusIn((short) date.getYear(),
                (short) date.getMonthValue(),
                List.of(PayrollStatus.COMPLETED.name(), PayrollStatus.PAID.name()));
    }

    void requireUnlocked(LocalDate date) {
        if (isLocked(date)) {
            throw new BusinessRuleException(ErrorCode.PAYROLL_LOCKED,
                    "Payroll for " + YearMonth.from(date) + " is already approved; attendance and leave are frozen");
        }
    }

    private void validate(RecordAttendanceRequest req, Employee e) {
        LocalDate date = req.attendanceDate();
        if (date.isBefore(e.getDateOfJoining())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Attendance date is before the employee's joining date");
        }
        if (e.getDateOfLeaving() != null && date.isAfter(e.getDateOfLeaving())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Attendance date is after the employee's leaving date");
        }
        if (date.isAfter(LocalDate.now(calendar.zone()))) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Attendance cannot be recorded for a future date");
        }
        boolean hasTimes = req.checkIn() != null || req.checkOut() != null;
        AttendanceStatus status = req.status();
        if (hasTimes && (status == AttendanceStatus.ABSENT || status == AttendanceStatus.HOLIDAY
                || status == AttendanceStatus.LEAVE)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    status + " attendance cannot have check-in or check-out times");
        }
        if (req.checkIn() != null && req.checkOut() != null && req.checkOut().isBefore(req.checkIn())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Check-out is before check-in");
        }
    }

    private Map<String, Shift> shiftsByCode() {
        Map<String, Shift> map = new HashMap<>();
        for (Shift s : shiftRepo.findAll()) {
            map.put(s.getShiftCode(), s);
        }
        return map;
    }

    AttendanceDto toDto(Attendance a, Employee e, Map<String, Shift> shifts) {
        BigDecimal hours = null;
        if (a.getCheckIn() != null && a.getCheckOut() != null) {
            hours = BigDecimal.valueOf(Duration.between(a.getCheckIn(), a.getCheckOut()).getSeconds())
                    .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
        }
        boolean late = false;
        Shift shift = a.getShift() == null ? null : shifts.get(a.getShift());
        boolean worked = a.getStatus().equals(AttendanceStatus.PRESENT.name())
                || a.getStatus().equals(AttendanceStatus.HALF_DAY.name());
        if (worked && shift != null && a.getCheckIn() != null) {
            LocalTime in = a.getCheckIn().atZone(calendar.zone()).toLocalTime();
            late = in.isAfter(shift.getStartTime().plusMinutes(shift.getGraceMinutes()));
        }
        return new AttendanceDto(a.getId(), e.getId(), e.getEmployeeCode(), e.fullName(), a.getAttendanceDate(),
                a.getShift(), a.getCheckIn(), a.getCheckOut(), a.getStatus(), hours, late, a.getSource(),
                a.getRemarks());
    }
}
