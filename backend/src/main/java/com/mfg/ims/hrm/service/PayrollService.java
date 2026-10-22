package com.mfg.ims.hrm.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.security.SegregationOfDuties;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
import com.mfg.ims.finance.gl.service.FiscalPeriodService;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.hrm.domain.Attendance;
import com.mfg.ims.hrm.domain.AttendanceStatus;
import com.mfg.ims.hrm.domain.Employee;
import com.mfg.ims.hrm.domain.LeaveApplication;
import com.mfg.ims.hrm.domain.LeaveType;
import com.mfg.ims.hrm.domain.PayrollRun;
import com.mfg.ims.hrm.domain.PayrollSlip;
import com.mfg.ims.hrm.domain.PayrollStatus;
import com.mfg.ims.hrm.dto.PayrollDtos.InitiatePayrollRequest;
import com.mfg.ims.hrm.dto.PayrollDtos.MarkPaidRequest;
import com.mfg.ims.hrm.dto.PayrollDtos.PayrollRunDto;
import com.mfg.ims.hrm.dto.PayrollDtos.PayrollSlipDto;
import com.mfg.ims.hrm.repository.AttendanceRepository;
import com.mfg.ims.hrm.repository.EmployeeRepository;
import com.mfg.ims.hrm.repository.LeaveApplicationRepository;
import com.mfg.ims.hrm.repository.LeaveTypeRepository;
import com.mfg.ims.hrm.repository.PayrollRunRepository;
import com.mfg.ims.hrm.repository.PayrollSlipRepository;

/**
 * Monthly payroll: initiate, calculate slips, approve (posts the payroll journal through
 * {@link JournalService}) and mark paid (posts the disbursement). Approval freezes the month's attendance and
 * leave. HR never writes journal tables or stock directly.
 */
@Service
public class PayrollService {

    static final String UNRECORDED_POLICY_KEY = "hrm.payroll.unrecorded_day_policy";
    private static final String UNRECORDED_ABSENT = "ABSENT";
    private static final String UNRECORDED_PRESENT = "PRESENT";

    static final String SALARY_EXPENSE = "5201";
    static final String EMPLOYER_PF_EXPENSE = "5205";
    static final String EMPLOYER_ESI_EXPENSE = "5206";
    static final String SALARY_PAYABLE = "2501";
    static final String PF_PAYABLE = "2502";
    static final String ESI_PAYABLE = "2503";
    static final String PT_PAYABLE = "2504";
    static final String TDS_PAYABLE = "2505";
    static final String BANK = "1201";

    private final PayrollRunRepository runRepo;
    private final PayrollSlipRepository slipRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final LeaveApplicationRepository leaveRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final SystemSettingService settings;
    private final HrmCalendar calendar;
    private final JournalService journalService;
    private final FiscalPeriodService fiscalPeriodService;
    private final GlAccountRepository glAccountRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public PayrollService(PayrollRunRepository runRepo, PayrollSlipRepository slipRepo,
                          EmployeeRepository employeeRepo, AttendanceRepository attendanceRepo,
                          LeaveApplicationRepository leaveRepo, LeaveTypeRepository leaveTypeRepo,
                          SystemSettingService settings, HrmCalendar calendar, JournalService journalService,
                          FiscalPeriodService fiscalPeriodService, GlAccountRepository glAccountRepo,
                          DocumentNumberService numberService, AuditService auditService) {
        this.runRepo = runRepo;
        this.slipRepo = slipRepo;
        this.employeeRepo = employeeRepo;
        this.attendanceRepo = attendanceRepo;
        this.leaveRepo = leaveRepo;
        this.leaveTypeRepo = leaveTypeRepo;
        this.settings = settings;
        this.calendar = calendar;
        this.journalService = journalService;
        this.fiscalPeriodService = fiscalPeriodService;
        this.glAccountRepo = glAccountRepo;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PayrollRunDto> list() {
        return runRepo.findAllByOrderByPeriodYearDescPeriodMonthDesc().stream().map(r -> toDto(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public PayrollRunDto get(Long id) {
        return toDto(require(id), true);
    }

    @Transactional(readOnly = true)
    public List<PayrollSlipDto> slips(Long runId) {
        PayrollRun run = require(runId);
        List<PayrollSlip> slips = slipRepo.findByPayrollRunIdOrderByEmployeeIdAsc(runId);
        Map<Long, Employee> employees = employees(slips);
        return slips.stream().map(s -> toDto(s, run, employees.get(s.getEmployeeId()))).toList();
    }

    @Transactional(readOnly = true)
    public PayrollSlipDto slip(Long slipId) {
        PayrollSlip s = slipRepo.findById(slipId).orElseThrow(() -> new NotFoundException("PayrollSlip", slipId));
        return toDto(s, require(s.getPayrollRunId()), employeeRepo.findById(s.getEmployeeId()).orElse(null));
    }

    @Transactional
    public PayrollRunDto initiate(InitiatePayrollRequest req) {
        YearMonth ym = YearMonth.of(req.year(), req.month());
        if (ym.isAfter(YearMonth.now(calendar.zone()))) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Payroll cannot be run for a future month");
        }
        if (runRepo.findByPeriodYearAndPeriodMonth((short) req.year().intValue(), (short) req.month().intValue())
                .isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "A payroll run already exists for " + ym);
        }
        requireOpenPeriod(ym.atEndOfMonth());

        PayrollRun run = new PayrollRun();
        run.setRunNumber(numberService.next("PAY"));
        run.setPeriodYear((short) req.year().intValue());
        run.setPeriodMonth((short) req.month().intValue());
        Long uid = HrmSecurity.currentUserId();
        run.setInitiatedBy(uid != null ? uid : -1L);
        run = runRepo.save(run);
        auditService.record("PAYROLL_INITIATE", "PAYROLL_RUN", String.valueOf(run.getId()), null,
                run.getRunNumber() + " " + ym, null);
        return toDto(run, false);
    }

    /** (Re)builds every slip of a DRAFT run from attendance, approved leave and the statutory settings. */
    @Transactional
    public PayrollRunDto calculate(Long id) {
        PayrollRun run = require(id);
        requireStatus(run, PayrollStatus.DRAFT, "calculated");
        YearMonth ym = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        requireOpenPeriod(last);

        PayrollSettings s = PayrollSettings.load(settings);
        PayrollRulePack rulePack = PayrollRulePacks.forCode(settings.get("hrm.payroll.rule_pack", PayrollRulePacks.DEFAULT.code()));
        String policy = unrecordedDayPolicy();
        LocalDate unrecordedBefore = UNRECORDED_ABSENT.equals(policy) ? LocalDate.now(calendar.zone()) : null;
        Set<DayOfWeek> weeklyOff = calendar.weeklyOff();
        Map<Long, List<Attendance>> attendance = attendanceRepo.findByAttendanceDateBetween(first, last).stream()
                .collect(Collectors.groupingBy(Attendance::getEmployeeId));
        Map<Long, List<LeaveApplication>> leaves = leaveRepo.findAllApprovedOverlapping(first, last).stream()
                .collect(Collectors.groupingBy(LeaveApplication::getEmployeeId));
        Map<Long, LeaveType> leaveTypes = leaveTypeRepo.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        slipRepo.deleteByPayrollRunId(run.getId());
        slipRepo.flush();

        List<PayrollSlip> slips = new ArrayList<>();
        for (Employee e : employeeRepo.findEmployedDuring(first, last)) {
            PayrollCalculator.Result r = PayrollCalculator.calculate(
                    inputs(e, ym, weeklyOff, attendance.getOrDefault(e.getId(), List.of()),
                            leaves.getOrDefault(e.getId(), List.of()), leaveTypes, unrecordedBefore), s, rulePack);
            if (r.grossPay().signum() == 0) {
                continue;
            }
            if (r.netPay().signum() < 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Net pay is negative for employee " + e.getEmployeeCode()
                                + "; review their pay, attendance and the statutory settings");
            }
            slips.add(slip(run, e, r));
        }
        if (slips.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No employee has payable days in " + ym);
        }
        slipRepo.saveAll(slips);

        run.setTotalEmployees(slips.size());
        run.setTotalGross(slips.stream().map(PayrollSlip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        run.setTotalNet(slips.stream().map(PayrollSlip::getNetPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        run = runRepo.save(run);
        auditService.record("PAYROLL_CALCULATE", "PAYROLL_RUN", String.valueOf(id), null,
                slips.size() + " slips; gross " + run.getTotalGross().toPlainString() + "; net "
                        + run.getTotalNet().toPlainString() + "; unrecorded-day policy " + policy
                        + "; rule pack " + rulePack.code(), null);
        return toDto(run, false);
    }

    /** Approves the run and posts the payroll journal in the same transaction (rules 13 and 14). */
    @Transactional
    public PayrollRunDto approve(Long id) {
        PayrollRun run = require(id);
        PayrollStatus.valueOf(run.getStatus()).transitionTo(PayrollStatus.COMPLETED);
        List<PayrollSlip> slips = slipRepo.findByPayrollRunIdOrderByEmployeeIdAsc(id);
        if (slips.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Calculate the run before approving it");
        }
        YearMonth ym = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        Long uid = HrmSecurity.currentUserId();
        // Segregation of duties (ARCHITECTURE section 10, requester != approver): the person who initiated the run
        // cannot approve it; approval is what posts the payroll journal, so it needs a second person.
        SegregationOfDuties.requireDifferentUser(uid, run.getInitiatedBy(),
                "You initiated payroll run " + run.getRunNumber()
                        + "; a different user with payroll rights must approve it");

        JournalHeaderDto journal = journalService.postSourceJournal(new AutoJournalRequest("PAYROLL", "hrm",
                "PAYROLL_RUN", run.getId(), ym.atEndOfMonth(), "Payroll " + run.getRunNumber() + " " + ym, uid,
                payrollLines(slips)));

        run.setStatus(PayrollStatus.COMPLETED.name());
        run.setJournalHeaderId(journal.id());
        run.setApprovedBy(uid);
        run.setCompletedAt(Instant.now());
        run = runRepo.save(run);
        auditService.record("PAYROLL_APPROVE", "PAYROLL_RUN", String.valueOf(id), PayrollStatus.DRAFT.name(),
                PayrollStatus.COMPLETED.name() + "; journal " + journal.journalNumber() + "; net "
                        + run.getTotalNet().toPlainString(), null);
        return toDto(run, true);
    }

    /** Records the bank disbursement: Dr Salary Payable / Cr Bank for the run's net pay. */
    @Transactional
    public PayrollRunDto markPaid(Long id, MarkPaidRequest req) {
        PayrollRun run = require(id);
        PayrollStatus.valueOf(run.getStatus()).transitionTo(PayrollStatus.PAID);
        LocalDate paymentDate = req != null && req.paymentDate() != null ? req.paymentDate()
                : LocalDate.now(calendar.zone());
        Long uid = HrmSecurity.currentUserId();
        BigDecimal net = run.getTotalNet();
        YearMonth ym = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());

        JournalHeaderDto journal = journalService.postSourceJournal(new AutoJournalRequest("PAYROLL", "hrm",
                "PAYROLL_PAYMENT", run.getId(), paymentDate, "Payroll payment " + run.getRunNumber() + " " + ym, uid,
                List.of(new JournalLineRequest(accountId(SALARY_PAYABLE), net, null, "Salary paid " + ym),
                        new JournalLineRequest(accountId(BANK), null, net, "Salary paid " + ym))));

        run.setStatus(PayrollStatus.PAID.name());
        run.setPaidAt(Instant.now());
        run.setPaidBy(uid);
        run.setPaymentJournalHeaderId(journal.id());
        run = runRepo.save(run);
        auditService.record("PAYROLL_PAY", "PAYROLL_RUN", String.valueOf(id), PayrollStatus.COMPLETED.name(),
                PayrollStatus.PAID.name() + "; journal " + journal.journalNumber(), null);
        return toDto(run, true);
    }

    // ---- internals ----------------------------------------------------------------------------------------

    /** ABSENT (default): working days with no attendance or leave record are LOP; PRESENT: they are paid. */
    private String unrecordedDayPolicy() {
        String raw = settings.get(UNRECORDED_POLICY_KEY, UNRECORDED_ABSENT).trim().toUpperCase();
        if (!UNRECORDED_ABSENT.equals(raw) && !UNRECORDED_PRESENT.equals(raw)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Setting " + UNRECORDED_POLICY_KEY + " must be ABSENT or PRESENT, not " + raw);
        }
        return raw;
    }

    private PayrollCalculator.Inputs inputs(Employee e, YearMonth ym, Set<DayOfWeek> weeklyOff,
                                            List<Attendance> rows, List<LeaveApplication> leaves,
                                            Map<Long, LeaveType> leaveTypes, LocalDate unrecordedBefore) {
        Map<AttendanceStatus, Set<LocalDate>> byStatus = new EnumMap<>(AttendanceStatus.class);
        for (Attendance a : rows) {
            byStatus.computeIfAbsent(AttendanceStatus.valueOf(a.getStatus()), k -> new HashSet<>())
                    .add(a.getAttendanceDate());
        }
        Set<LocalDate> halfDays = new HashSet<>(byStatus.getOrDefault(AttendanceStatus.HALF_DAY, Set.of()));
        Set<LocalDate> paidLeave = new HashSet<>();
        Set<LocalDate> unpaidLeave = new HashSet<>();
        Set<LocalDate> recorded = new HashSet<>();
        for (Attendance a : rows) {
            recorded.add(a.getAttendanceDate());
        }
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        for (LeaveApplication l : leaves) {
            boolean paid = leaveTypes.get(l.getLeaveTypeId()).isPaid();
            LocalDate from = l.getFromDate().isBefore(first) ? first : l.getFromDate();
            LocalDate to = l.getToDate().isAfter(last) ? last : l.getToDate();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                recorded.add(d);
                if (l.isHalfDay()) {
                    if (!paid) {
                        halfDays.add(d);
                    }
                } else if (paid) {
                    paidLeave.add(d);
                } else {
                    unpaidLeave.add(d);
                }
            }
        }
        return new PayrollCalculator.Inputs(ym, e.getDateOfJoining(), e.getDateOfLeaving(), weeklyOff,
                byStatus.getOrDefault(AttendanceStatus.HOLIDAY, Set.of()),
                byStatus.getOrDefault(AttendanceStatus.ABSENT, Set.of()), halfDays, paidLeave, unpaidLeave,
                recorded, unrecordedBefore, e.getBasicPay(), e.getFixedTravelAllowance(), e.getFixedOtherAllowances());
    }

    private static PayrollSlip slip(PayrollRun run, Employee e, PayrollCalculator.Result r) {
        PayrollSlip s = new PayrollSlip();
        s.setPayrollRunId(run.getId());
        s.setEmployeeId(e.getId());
        s.setCostCentreId(e.getCostCentreId());
        s.setBasicPay(r.basicPay());
        s.setHra(r.hra());
        s.setDa(r.da());
        s.setTravelAllowance(r.travelAllowance());
        s.setOtherAllowances(r.otherAllowances());
        s.setGrossPay(r.grossPay());
        s.setPfEmployee(r.pfEmployee());
        s.setPfEmployer(r.pfEmployer());
        s.setEsiEmployee(r.esiEmployee());
        s.setEsiEmployer(r.esiEmployer());
        s.setProfessionalTax(r.professionalTax());
        s.setTds(r.tds());
        s.setTotalDeductions(r.totalDeductions());
        s.setNetPay(r.netPay());
        s.setWorkingDays(r.workingDays());
        s.setPaidDays(r.paidDays());
        s.setLopDays(r.lopDays());
        return s;
    }

    /**
     * Dr salary and employer contributions (per cost centre), Cr salary payable and each statutory payable.
     * Zero amounts are left out because a journal line must carry exactly one non-zero side.
     */
    private List<JournalLineRequest> payrollLines(List<PayrollSlip> slips) {
        Map<Long, BigDecimal[]> byCostCentre = new LinkedHashMap<>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal pfEmployee = BigDecimal.ZERO;
        BigDecimal pfEmployer = BigDecimal.ZERO;
        BigDecimal esiEmployee = BigDecimal.ZERO;
        BigDecimal esiEmployer = BigDecimal.ZERO;
        BigDecimal pt = BigDecimal.ZERO;
        BigDecimal tds = BigDecimal.ZERO;
        for (PayrollSlip s : slips) {
            BigDecimal[] totals = byCostCentre.computeIfAbsent(s.getCostCentreId() == null ? 0L : s.getCostCentreId(),
                    k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(s.getGrossPay());
            totals[1] = totals[1].add(s.getPfEmployer());
            totals[2] = totals[2].add(s.getEsiEmployer());
            net = net.add(s.getNetPay());
            pfEmployee = pfEmployee.add(s.getPfEmployee());
            pfEmployer = pfEmployer.add(s.getPfEmployer());
            esiEmployee = esiEmployee.add(s.getEsiEmployee());
            esiEmployer = esiEmployer.add(s.getEsiEmployer());
            pt = pt.add(s.getProfessionalTax());
            tds = tds.add(s.getTds());
        }

        List<JournalLineRequest> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : byCostCentre.entrySet()) {
            Long costCentre = e.getKey() == 0L ? null : e.getKey();
            addDebit(lines, SALARY_EXPENSE, e.getValue()[0], "Salaries", costCentre);
            addDebit(lines, EMPLOYER_PF_EXPENSE, e.getValue()[1], "Employer PF", costCentre);
            addDebit(lines, EMPLOYER_ESI_EXPENSE, e.getValue()[2], "Employer ESI", costCentre);
        }
        addCredit(lines, SALARY_PAYABLE, net, "Net salary payable");
        addCredit(lines, PF_PAYABLE, pfEmployee.add(pfEmployer), "PF payable (employee + employer)");
        addCredit(lines, ESI_PAYABLE, esiEmployee.add(esiEmployer), "ESI payable (employee + employer)");
        addCredit(lines, PT_PAYABLE, pt, "Professional tax payable");
        addCredit(lines, TDS_PAYABLE, tds, "TDS payable");
        return lines;
    }

    private void addDebit(List<JournalLineRequest> lines, String account, BigDecimal amount, String text,
                          Long costCentreId) {
        if (amount.signum() > 0) {
            lines.add(new JournalLineRequest(accountId(account), amount, null, text, costCentreId, null));
        }
    }

    private void addCredit(List<JournalLineRequest> lines, String account, BigDecimal amount, String text) {
        if (amount.signum() > 0) {
            lines.add(new JournalLineRequest(accountId(account), null, amount, text));
        }
    }

    private Long accountId(String code) {
        return glAccountRepo.findByAccountCode(code)
                .orElseThrow(() -> new NotFoundException("GL account " + code + " is not configured"))
                .getId();
    }

    private void requireOpenPeriod(LocalDate date) {
        fiscalPeriodService.findOpenPeriodForDate(date).orElseThrow(() -> new BusinessRuleException(
                ErrorCode.PERIOD_CLOSED, "No open fiscal period for " + date + "; payroll cannot be posted"));
    }

    private static void requireStatus(PayrollRun run, PayrollStatus expected, String verb) {
        if (!expected.name().equals(run.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Payroll run " + run.getRunNumber() + " is " + run.getStatus() + " and cannot be " + verb);
        }
    }

    private PayrollRun require(Long id) {
        return runRepo.findById(id).orElseThrow(() -> new NotFoundException("PayrollRun", id));
    }

    private Map<Long, Employee> employees(List<PayrollSlip> slips) {
        return employeeRepo.findAllById(slips.stream().map(PayrollSlip::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private PayrollRunDto toDto(PayrollRun r, boolean withJournalNumber) {
        String journalNumber = null;
        if (withJournalNumber && r.getJournalHeaderId() != null) {
            journalNumber = journalService.findById(r.getJournalHeaderId()).journalNumber();
        }
        List<String> actions = switch (PayrollStatus.valueOf(r.getStatus())) {
            case DRAFT -> List.of("CALCULATE", "APPROVE");
            case COMPLETED -> List.of("MARK_PAID");
            case PAID -> List.of();
        };
        return new PayrollRunDto(r.getId(), r.getRunNumber(), r.getPeriodMonth(), r.getPeriodYear(), r.getStatus(),
                r.getInitiatedBy(), r.getCompletedAt(), r.getApprovedBy(), r.getTotalEmployees(), r.getTotalGross(),
                r.getTotalNet(), r.getJournalHeaderId(), journalNumber, r.getPaidAt(), r.getPaidBy(),
                r.getPaymentJournalHeaderId(), r.getVersion(), actions);
    }

    private static PayrollSlipDto toDto(PayrollSlip s, PayrollRun run, Employee e) {
        return new PayrollSlipDto(s.getId(), s.getPayrollRunId(), run.getRunNumber(), run.getPeriodMonth(),
                run.getPeriodYear(), s.getEmployeeId(), e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.fullName(), e == null ? null : e.getDesignation(),
                e == null ? null : e.getDepartment(), s.getCostCentreId(), s.getBasicPay(), s.getHra(), s.getDa(),
                s.getTravelAllowance(), s.getOtherAllowances(), s.getGrossPay(), s.getPfEmployee(),
                s.getPfEmployer(), s.getEsiEmployee(), s.getEsiEmployer(), s.getProfessionalTax(), s.getTds(),
                s.getTotalDeductions(), s.getNetPay(), s.getWorkingDays(), s.getPaidDays(), s.getLopDays());
    }
}
