package com.mfg.ims.hrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.service.FiscalPeriodService;
import com.mfg.ims.hrm.domain.Attendance;
import com.mfg.ims.hrm.domain.AttendanceStatus;
import com.mfg.ims.hrm.domain.EmploymentType;
import com.mfg.ims.hrm.domain.PayrollSlip;
import com.mfg.ims.hrm.dto.AttendanceDtos.RecordAttendanceRequest;
import com.mfg.ims.hrm.dto.EmployeeDtos.EmployeeDto;
import com.mfg.ims.hrm.dto.EmployeeDtos.EmployeeSummaryDto;
import com.mfg.ims.hrm.dto.EmployeeDtos.SaveEmployeeRequest;
import com.mfg.ims.hrm.dto.LeaveDtos.ApplyLeaveRequest;
import com.mfg.ims.hrm.dto.LeaveDtos.LeaveApplicationDto;
import com.mfg.ims.hrm.dto.PayrollDtos.InitiatePayrollRequest;
import com.mfg.ims.hrm.dto.PayrollDtos.MarkPaidRequest;
import com.mfg.ims.hrm.dto.PayrollDtos.PayrollRunDto;
import com.mfg.ims.hrm.dto.SkillTrainingDtos.SaveSkillRequest;
import com.mfg.ims.hrm.domain.SkillType;
import com.mfg.ims.hrm.repository.AttendanceRepository;
import com.mfg.ims.hrm.repository.PayrollSlipRepository;
import com.mfg.ims.hrm.service.AttendanceService;
import com.mfg.ims.hrm.service.EmployeeService;
import com.mfg.ims.hrm.service.LeaveService;
import com.mfg.ims.hrm.service.PayrollService;
import com.mfg.ims.hrm.service.SkillService;
import com.mfg.ims.production.domain.ProductionOperation;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.repository.ProductionOperationRepository;
import com.mfg.ims.production.repository.ProductionOrderRepository;
import com.mfg.ims.production.service.OperationExecutionService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Integration tests for R13 HRM against real PostgreSQL. Months are chosen so their weekdays are known:
 * May 2026 (Fri 1st), June 2026 (Mon 1st), July 2026 (Wed 1st), August 2026 (Sat 1st); with Sunday as the weekly
 * off they have 26, 26, 27 and 26 working days. Database tests roll back; the auto-capture test must commit
 * because the capture runs after the operation's transaction.
 */
class HrmIT extends AbstractIntegrationTest {

    private static final LocalDate JOINED = LocalDate.of(2025, 1, 1);
    private static final String POLICY_KEY = "hrm.payroll.unrecorded_day_policy";

    @Autowired EmployeeService employeeService;
    @Autowired AttendanceService attendanceService;
    @Autowired LeaveService leaveService;
    @Autowired PayrollService payrollService;
    @Autowired SkillService skillService;
    @Autowired PayrollSlipRepository slipRepo;
    @Autowired AttendanceRepository attendanceRepo;
    @Autowired FiscalPeriodService periodService;
    @Autowired FiscalPeriodRepository periodRepo;
    @Autowired OperationExecutionService operationService;
    @Autowired ProductionOrderRepository orderRepo;
    @Autowired ProductionOperationRepository opRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.cache.CacheManager cacheManager;
    @Autowired com.mfg.ims.common.settings.SystemSettingService systemSettings;
    @Autowired TestData td;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        cacheManager.getCache("systemSettings").evict(POLICY_KEY);
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private void as(Long userId, String... authorities) {
        var token = new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private Long user() {
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'HR Tester', ?, 'x') RETURNING id
                """, Long.class, td.code("hr"), td.code("hr") + "@example.com");
    }

    private Long costCentre() {
        return jdbc.queryForObject("""
                INSERT INTO ims.cost_centres (cost_centre_code, name) VALUES (?, 'HR test centre') RETURNING id
                """, Long.class, td.code("CCH"));
    }

    private EmployeeDto employee(String basic, Long userId, Long costCentreId) {
        return employeeService.create(new SaveEmployeeRequest(null, userId, "Test", td.code("Emp"), null, null,
                "Operator", "Production", null, costCentreId, JOINED, EmploymentType.PERMANENT, null,
                new BigDecimal(basic), null, null, "123456789", "HDFC0000001", null, null, null, null, null));
    }

    private void closePeriodContaining(LocalDate date) {
        FiscalPeriod p = periodRepo.findOpenPeriodForDate(date).orElseThrow();
        periodService.initiatePeriodClose(p.getId());
        periodService.finalizePeriodClose(p.getId(), "test close");
    }

    /** Pins the legacy PRESENT policy so tests about other payroll rules need no attendance for every workday. */
    private PayrollRunDto run(int month, int year) {
        return run("PRESENT", month, year);
    }

    private PayrollRunDto run(String unrecordedPolicy, int month, int year) {
        systemSettings.update(POLICY_KEY, unrecordedPolicy);
        PayrollRunDto run = payrollService.initiate(new InitiatePayrollRequest(month, year));
        return payrollService.calculate(run.id());
    }

    /** Marks the employee PRESENT on every August 2026 working day (Sundays off) except the listed days. */
    private void presentOnAugustWorkdaysExcept(EmployeeDto e, int... skippedDays) {
        for (int day = 1; day <= 31; day++) {
            LocalDate d = LocalDate.of(2026, 8, day);
            int dayOfMonth = day;
            boolean skipped = Arrays.stream(skippedDays).anyMatch(x -> x == dayOfMonth);
            if (d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY && !skipped) {
                attendanceService.record(new RecordAttendanceRequest(e.id(), d, null, null, null,
                        AttendanceStatus.PRESENT, null));
            }
        }
    }

    private PayrollSlip slipOf(Long runId, Long employeeId) {
        return slipRepo.findByPayrollRunIdOrderByEmployeeIdAsc(runId).stream()
                .filter(s -> s.getEmployeeId().equals(employeeId)).findFirst().orElseThrow();
    }

    private ApplyLeaveRequest leave(String code, LocalDate from, LocalDate to) {
        Long typeId = jdbc.queryForObject("select id from ims.leave_types where leave_code = ?", Long.class, code);
        return new ApplyLeaveRequest(null, typeId, from, to, false, "personal");
    }

    // ---- payroll ------------------------------------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("Payroll run calculates slips, then approval posts one balanced journal and payment clears payable")
    void payrollRunPostsBalancedJournal() {
        Long cc = costCentre();
        EmployeeDto low = employee("12000", null, null);
        EmployeeDto high = employee("30000", null, cc);

        PayrollRunDto calculated = run(5, 2026);
        PayrollSlip lowSlip = slipOf(calculated.id(), low.id());
        assertThat(lowSlip.getGrossPay()).isEqualByComparingTo("16800.00");
        assertThat(lowSlip.getNetPay()).isEqualByComparingTo("15034.00");
        assertThat(lowSlip.getWorkingDays()).isEqualByComparingTo("26.0");
        assertThat(slipOf(calculated.id(), high.id()).getPfEmployee()).isEqualByComparingTo("1800.00");

        PayrollRunDto approved = td.asSecondUser(() -> payrollService.approve(calculated.id()), "PAYROLL_RUN");
        assertThat(approved.status()).isEqualTo("COMPLETED");
        assertThat(approved.journalHeaderId()).isNotNull();

        List<PayrollSlip> slips = slipRepo.findByPayrollRunIdOrderByEmployeeIdAsc(calculated.id());
        BigDecimal gross = sum(slips, PayrollSlip::getGrossPay);
        BigDecimal net = sum(slips, PayrollSlip::getNetPay);
        BigDecimal pfEr = sum(slips, PayrollSlip::getPfEmployer);
        BigDecimal esiEr = sum(slips, PayrollSlip::getEsiEmployer);

        Map<String, Object> header = jdbc.queryForMap(
                "select status, source_document_type, posting_date from ims.journal_headers where id = ?",
                approved.journalHeaderId());
        assertThat(header.get("status")).isEqualTo("POSTED");
        assertThat(header.get("source_document_type")).isEqualTo("PAYROLL_RUN");
        assertThat(header.get("posting_date").toString()).isEqualTo("2026-05-31");

        BigDecimal debit = jdbc.queryForObject(
                "select coalesce(sum(debit_amount),0) from ims.journal_lines where journal_header_id = ?",
                BigDecimal.class, approved.journalHeaderId());
        BigDecimal credit = jdbc.queryForObject(
                "select coalesce(sum(credit_amount),0) from ims.journal_lines where journal_header_id = ?",
                BigDecimal.class, approved.journalHeaderId());
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo(gross.add(pfEr).add(esiEr));
        assertThat(accountTotal(approved.journalHeaderId(), "2501", "credit_amount")).isEqualByComparingTo(net);
        assertThat(jdbc.queryForObject("""
                select count(*) from ims.journal_lines l join ims.gl_accounts a on a.id = l.gl_account_id
                where l.journal_header_id = ? and a.account_code = '5201' and l.cost_centre_id = ?
                """, Integer.class, approved.journalHeaderId(), cc)).isEqualTo(1);

        PayrollRunDto paid = payrollService.markPaid(approved.id(), new MarkPaidRequest(LocalDate.of(2026, 6, 5)));
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(accountTotal(paid.paymentJournalHeaderId(), "2501", "debit_amount")).isEqualByComparingTo(net);
        assertThat(accountTotal(paid.paymentJournalHeaderId(), "1201", "credit_amount")).isEqualByComparingTo(net);
    }

    @Test
    @Transactional
    @DisplayName("Payroll is refused for a closed period, and approval fails if the period closes after calculation")
    void payrollRespectsClosedPeriods() {
        employee("12000", null, null);

        closePeriodContaining(LocalDate.of(2026, 6, 15));
        assertThatThrownBy(() -> payrollService.initiate(new InitiatePayrollRequest(6, 2026)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.PERIOD_CLOSED);

        PayrollRunDto calculated = run(7, 2026);
        closePeriodContaining(LocalDate.of(2026, 7, 15));
        assertThatThrownBy(() -> td.asSecondUser(() -> payrollService.approve(calculated.id()), "PAYROLL_RUN"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.PERIOD_CLOSED);
        assertThat(payrollService.get(calculated.id()).status()).isEqualTo("DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("A second run for the same month and a run for a future month are rejected")
    void payrollRunIsUniquePerMonthAndNeverFuture() {
        payrollService.initiate(new InitiatePayrollRequest(5, 2026));
        assertThatThrownBy(() -> payrollService.initiate(new InitiatePayrollRequest(5, 2026)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> payrollService.initiate(new InitiatePayrollRequest(12, 2099)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @Transactional
    @DisplayName("SOD-PAY-1: the user who initiated a payroll run cannot approve it; a different user can")
    void initiatorCannotApproveTheirOwnPayrollRun() {
        employee("12000", null, null);
        PayrollRunDto draft = td.asUser(7_920_001L, () -> run(9, 2026), "PAYROLL_RUN");
        assertThat(draft.initiatedBy()).isEqualTo(7_920_001L);

        assertThatThrownBy(() -> td.asUser(7_920_001L, () -> payrollService.approve(draft.id()), "PAYROLL_RUN"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);
        assertThat(payrollService.get(draft.id()).status()).isEqualTo("DRAFT");

        PayrollRunDto approved = td.asUser(7_920_002L, () -> payrollService.approve(draft.id()), "PAYROLL_RUN");
        assertThat(approved.status()).isEqualTo("COMPLETED");
        assertThat(approved.approvedBy()).isEqualTo(7_920_002L);
        assertThat(approved.initiatedBy()).isEqualTo(7_920_001L);
        assertThat(approved.journalHeaderId()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Approved payroll freezes the month's attendance and leave; draft runs do not")
    void approvedPayrollFreezesAttendanceAndLeave() {
        EmployeeDto e = employee("12000", user(), null);
        PayrollRunDto draft = run(8, 2026);
        attendanceService.record(new RecordAttendanceRequest(e.id(), LocalDate.of(2026, 8, 4), null, null, null,
                AttendanceStatus.ABSENT, "before approval"));

        payrollService.calculate(draft.id());
        td.runAsSecondUser(() -> payrollService.approve(draft.id()), "PAYROLL_RUN");

        assertThatThrownBy(() -> attendanceService.record(new RecordAttendanceRequest(e.id(),
                LocalDate.of(2026, 8, 5), null, null, null, AttendanceStatus.ABSENT, "late")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.PAYROLL_LOCKED);
        as(e.userId(), "LEAVE_APPLY");
        assertThatThrownBy(() -> leaveService.apply(leave("CL", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.PAYROLL_LOCKED);
    }

    @Test
    @Transactional
    @DisplayName("Absences reduce the slip: two absent days in a 26-day month pay 24/26 of basic")
    void absencesProrateTheSlip() {
        EmployeeDto e = employee("26000", null, null);
        for (int day : new int[] {3, 4}) {
            attendanceService.record(new RecordAttendanceRequest(e.id(), LocalDate.of(2026, 8, day), null, null, null,
                    AttendanceStatus.ABSENT, null));
        }
        PayrollRunDto run = run(8, 2026);
        PayrollSlip slip = slipOf(run.id(), e.id());

        assertThat(slip.getPaidDays()).isEqualByComparingTo("24.0");
        assertThat(slip.getLopDays()).isEqualByComparingTo("2.0");
        assertThat(slip.getBasicPay()).isEqualByComparingTo("24000.00");
    }


    @Test
    @Transactional
    @DisplayName("Absent policy: two unrecorded working days are loss of pay (24/26 of basic); present policy pays them")
    void unrecordedWorkdaysAreLossOfPayUnderTheAbsentPolicy() {
        EmployeeDto e = employee("26000", null, null);
        presentOnAugustWorkdaysExcept(e, 3, 4);

        PayrollRunDto absentRun = run("ABSENT", 8, 2026);
        PayrollSlip slip = slipOf(absentRun.id(), e.id());
        assertThat(slip.getPaidDays()).isEqualByComparingTo("24.0");
        assertThat(slip.getLopDays()).isEqualByComparingTo("2.0");
        assertThat(slip.getBasicPay()).isEqualByComparingTo("24000.00");

        systemSettings.update(POLICY_KEY, "PRESENT");
        PayrollRunDto presentRun = payrollService.calculate(absentRun.id());
        PayrollSlip paid = slipOf(presentRun.id(), e.id());
        assertThat(paid.getPaidDays()).isEqualByComparingTo("26.0");
        assertThat(paid.getBasicPay()).isEqualByComparingTo("26000.00");
    }

    @Test
    @Transactional
    @DisplayName("Absent policy: an employee with no attendance records gets no slip while a fully recorded one does")
    void employeeWithNoRecordsEarnsNothingUnderTheAbsentPolicy() {
        EmployeeDto recorded = employee("26000", null, null);
        EmployeeDto none = employee("26000", null, null);
        presentOnAugustWorkdaysExcept(recorded);

        PayrollRunDto run = run("ABSENT", 8, 2026);

        assertThat(slipOf(run.id(), recorded.id()).getPaidDays()).isEqualByComparingTo("26.0");
        assertThat(slipRepo.findByPayrollRunIdOrderByEmployeeIdAsc(run.id()))
                .extracting(PayrollSlip::getEmployeeId).doesNotContain(none.id());
    }

    @Test
    @Transactional
    @DisplayName("Absent policy: a holiday, Sundays and an approved paid leave day are not penalised")
    void holidaysWeeklyOffsAndPaidLeaveAreNotPenalised() {
        Long applicantUser = user();
        EmployeeDto e = employee("25000", applicantUser, null);
        attendanceService.record(new RecordAttendanceRequest(e.id(), LocalDate.of(2026, 8, 12), null, null, null,
                AttendanceStatus.HOLIDAY, null));
        as(applicantUser, "LEAVE_APPLY");
        LeaveApplicationDto applied = leaveService.apply(leave("CL", LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)));
        as(user(), "LEAVE_APPROVE");
        leaveService.approve(applied.id(), null);
        SecurityContextHolder.clearContext();
        presentOnAugustWorkdaysExcept(e, 12, 13);

        PayrollSlip slip = slipOf(run("ABSENT", 8, 2026).id(), e.id());

        assertThat(slip.getWorkingDays()).isEqualByComparingTo("25.0");
        assertThat(slip.getPaidDays()).isEqualByComparingTo("25.0");
        assertThat(slip.getLopDays()).isEqualByComparingTo("0.0");
        assertThat(slip.getBasicPay()).isEqualByComparingTo("25000.00");
    }

    @Test
    @Transactional
    @DisplayName("An unknown unrecorded-day policy value is refused")
    void unknownPolicyIsRefused() {
        employee("12000", null, null);

        assertThatThrownBy(() -> run("SOMETIMES", 8, 2026))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- leave --------------------------------------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("D-19: a user with no employee record has no leave balances (empty list, not a 404)")
    void leaveBalancesAreEmptyForAUserWithoutAnEmployeeRecord() {
        Long unlinkedUser = user();
        as(unlinkedUser, "LEAVE_APPLY");
        assertThat(leaveService.balances(null, 2026)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Leave workflow: apply, approve deducts the balance and marks attendance, cancel restores both")
    void leaveWorkflow() {
        Long applicantUser = user();
        Long managerUser = user();
        EmployeeDto applicant = employee("20000", applicantUser, null);

        as(applicantUser, "LEAVE_APPLY");
        LeaveApplicationDto applied = leaveService.apply(leave("CL", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2)));
        assertThat(applied.status()).isEqualTo("PENDING");
        assertThat(applied.days()).isEqualByComparingTo("2.0");
        assertThat(applied.allowedActions()).containsExactly("CANCEL");

        as(managerUser, "LEAVE_APPROVE");
        LeaveApplicationDto approved = leaveService.approve(applied.id(), "enjoy");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decidedBy()).isEqualTo(managerUser);
        assertThat(leaveService.balances(applicant.id(), 2026).stream()
                .filter(b -> b.leaveCode().equals("CL")).findFirst().orElseThrow().used()).isEqualByComparingTo("2.0");
        List<Attendance> marked = attendanceRepo.findByEmployeeIdAndAttendanceDateBetween(applicant.id(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));
        assertThat(marked).hasSize(2).allMatch(a -> a.getStatus().equals("LEAVE"));

        LeaveApplicationDto cancelled = leaveService.cancel(applied.id(), "plans changed");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(leaveService.balances(applicant.id(), 2026).stream()
                .filter(b -> b.leaveCode().equals("CL")).findFirst().orElseThrow().used()).isEqualByComparingTo("0.0");
        assertThat(attendanceRepo.findByEmployeeIdAndAttendanceDateBetween(applicant.id(), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2))).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Leave rules: no self-approval, reject needs a reason, no overlap, no overdraft")
    void leaveRules() {
        Long applicantUser = user();
        employee("20000", applicantUser, null);

        as(applicantUser, "LEAVE_APPLY", "LEAVE_APPROVE");
        LeaveApplicationDto applied = leaveService.apply(leave("CL", LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8)));
        assertThatThrownBy(() -> leaveService.approve(applied.id(), null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);

        assertThatThrownBy(() -> leaveService.apply(leave("SL", LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.LEAVE_OVERLAP);

        // Jun 10-26 holds 15 working days, more than the 12 casual days a year.
        assertThatThrownBy(() -> leaveService.apply(leave("CL", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 26))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.LEAVE_BALANCE_INSUFFICIENT);

        Long managerUser = user();
        as(managerUser, "LEAVE_APPROVE");
        assertThatThrownBy(() -> leaveService.reject(applied.id(), " "))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(leaveService.reject(applied.id(), "busy period").status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> leaveService.approve(applied.id(), null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    @Transactional
    @DisplayName("Approved unpaid leave (LOP) needs no balance and reduces that month's pay")
    void unpaidLeaveFlowsIntoPayroll() {
        Long applicantUser = user();
        EmployeeDto e = employee("27000", applicantUser, null);

        as(applicantUser, "LEAVE_APPLY");
        LeaveApplicationDto applied = leaveService.apply(leave("LOP", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)));
        as(user(), "LEAVE_APPROVE");
        leaveService.approve(applied.id(), null);
        SecurityContextHolder.clearContext();

        PayrollSlip slip = slipOf(run(7, 2026).id(), e.id());
        assertThat(slip.getWorkingDays()).isEqualByComparingTo("27.0");
        assertThat(slip.getLopDays()).isEqualByComparingTo("2.0");
        assertThat(slip.getBasicPay()).isEqualByComparingTo("25000.00");
    }

    // ---- employees, pay data, skills ----------------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("Pay and bank data is hidden without EMPLOYEE_MANAGE or PAYROLL_RUN")
    void compensationIsMasked() {
        Long id = employee("50000", null, null).id();

        as(user(), "HR_VIEW");
        EmployeeDto limited = employeeService.findById(id);
        assertThat(limited.compensationVisible()).isFalse();
        assertThat(limited.basicPay()).isNull();
        assertThat(limited.bankAccountNo()).isNull();

        as(user(), "HR_VIEW", "EMPLOYEE_MANAGE");
        EmployeeDto full = employeeService.findById(id);
        assertThat(full.compensationVisible()).isTrue();
        assertThat(full.basicPay()).isEqualByComparingTo("50000");
        assertThat(full.bankAccountNo()).isEqualTo("123456789");
    }

    @Test
    @Transactional
    @DisplayName("Changing pay needs a reason and writes an audit entry; linking one user twice is refused")
    void payChangeIsAuditedAndUserLinkIsUnique() {
        Long linked = user();
        EmployeeDto e = employee("40000", linked, null);

        SaveEmployeeRequest raise = new SaveEmployeeRequest(e.employeeCode(), linked, e.firstName(), e.lastName(), null,
                null, "Operator", "Production", null, null, JOINED, EmploymentType.PERMANENT, null,
                new BigDecimal("45000"), null, null, null, null, null, null, null, null, e.version());
        assertThatThrownBy(() -> employeeService.update(e.id(), raise))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);

        SaveEmployeeRequest withReason = new SaveEmployeeRequest(e.employeeCode(), linked, e.firstName(),
                e.lastName(), null, null, "Operator", "Production", null, null, JOINED, EmploymentType.PERMANENT, null,
                new BigDecimal("45000"), null, null, null, null, null, null, null, "annual increment", e.version());
        employeeService.update(e.id(), withReason);
        assertThat(jdbc.queryForObject("""
                select count(*) from ims.audit_logs where action = 'EMPLOYEE_PAY_CHANGE' and entity_id = ?
                """, Integer.class, String.valueOf(e.id()))).isEqualTo(1);

        assertThatThrownBy(() -> employee("30000", linked, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    @Transactional
    @DisplayName("The skill matrix lists only active, non-expired authorisations for a machine")
    void authorisedOperators() {
        Long machine = jdbc.queryForObject(
                "insert into ims.machines (machine_code, name) values (?, 'HR test machine') returning id", Long.class,
                td.code("MCH"));
        EmployeeDto certified = employee("10000", null, null);
        EmployeeDto expired = employee("10000", null, null);
        EmployeeDto other = employee("10000", null, null);

        skillService.add(certified.id(), new SaveSkillRequest(SkillType.MACHINE, machine, null, null, null, 4,
                LocalDate.of(2025, 1, 1), LocalDate.now().plusYears(1), true));
        skillService.add(expired.id(), new SaveSkillRequest(SkillType.MACHINE, machine, null, null, null, 3,
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), true));
        skillService.add(other.id(), new SaveSkillRequest(SkillType.CERTIFICATION, null, null, "Safety", "C-1", null,
                null, null, true));

        assertThat(skillService.authorised(machine, null).stream().map(EmployeeSummaryDto::id).toList())
                .containsExactly(certified.id());
        assertThatThrownBy(() -> skillService.authorised(null, null)).isInstanceOf(BusinessRuleException.class);
    }

    // ---- attendance auto-capture (commits: the capture runs after the operation transaction) --------------

    @Test
    @DisplayName("Starting and completing a production operation records the operator's attendance")
    void attendanceIsCapturedFromProductionOperations() {
        Long operatorUser = user();
        EmployeeDto operator = employee("15000", operatorUser, null);
        employeeService.update(operator.id(), new SaveEmployeeRequest(operator.employeeCode(), operatorUser,
                operator.firstName(), operator.lastName(), null, null, "Operator", "Production", null, null, JOINED,
                EmploymentType.PERMANENT, null, null, null, null, null, null, null, null, "GENERAL", null,
                operator.version()));

        Long productId = td.product().getId();
        ProductionOrder order = new ProductionOrder();
        order.setOrderNumber(td.code("HR"));
        order.setProductId(productId);
        order.setBomId(td.draftBom(productId).getId());
        order.setPlannedQty(BigDecimal.TEN);
        order.setStatus("IN_PROGRESS");
        order = orderRepo.save(order);
        Long operationMaster = jdbc.queryForObject("select id from ims.operations order by id limit 1", Long.class);
        Long first = operation(order.getId(), 1, operationMaster, operatorUser);
        operation(order.getId(), 2, operationMaster, null);

        operationService.startOperation(first, null);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Attendance afterStart = attendanceRepo.findByEmployeeIdAndAttendanceDate(operator.id(), today).orElseThrow();
        assertThat(afterStart.getStatus()).isEqualTo("PRESENT");
        assertThat(afterStart.getSource()).isEqualTo("AUTO");
        assertThat(afterStart.getShift()).isEqualTo("GENERAL");
        assertThat(afterStart.getCheckIn()).isNotNull();
        assertThat(afterStart.getCheckOut()).isNull();

        operationService.completeOperation(first);
        Attendance afterComplete = attendanceRepo.findByEmployeeIdAndAttendanceDate(operator.id(), today).orElseThrow();
        assertThat(afterComplete.getCheckOut()).isNotNull();
        assertThat(afterComplete.getCheckOut()).isAfterOrEqualTo(afterComplete.getCheckIn());
        assertThat(afterComplete.getCheckIn()).isEqualTo(afterStart.getCheckIn());
    }

    @Test
    @DisplayName("Auto-capture ignores operators without an employee record and never fails the caller")
    void autoCaptureIgnoresUnlinkedOperators() {
        long before = attendanceRepo.count();
        attendanceService.autoCapture(user(), Instant.now(), false);
        attendanceService.autoCapture(null, Instant.now(), false);
        assertThat(attendanceRepo.count()).isEqualTo(before);
    }

    private Long operation(Long orderId, int sequence, Long operationMaster, Long operatorUser) {
        ProductionOperation op = new ProductionOperation();
        op.setProductionOrderId(orderId);
        op.setSequenceNo(sequence);
        op.setOperationId(operationMaster);
        op.setAssignedOperatorId(operatorUser);
        return opRepo.save(op).getId();
    }

    private BigDecimal accountTotal(Long journalId, String accountCode, String column) {
        return jdbc.queryForObject("select coalesce(sum(l." + column + "),0) from ims.journal_lines l "
                + "join ims.gl_accounts a on a.id = l.gl_account_id where l.journal_header_id = ? "
                + "and a.account_code = ?", BigDecimal.class, journalId, accountCode);
    }

    private static BigDecimal sum(List<PayrollSlip> slips, java.util.function.Function<PayrollSlip, BigDecimal> f) {
        return slips.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
