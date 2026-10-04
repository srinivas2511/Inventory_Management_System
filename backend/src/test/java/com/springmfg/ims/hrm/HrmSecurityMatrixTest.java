package com.springmfg.ims.hrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springmfg.ims.common.exception.GlobalExceptionHandler;
import com.springmfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.springmfg.ims.config.CorrelationIdFilter;
import com.springmfg.ims.config.JwtAuthenticationFilter;
import com.springmfg.ims.config.ProblemDetailSecurityHandlers;
import com.springmfg.ims.config.SecurityConfig;
import com.springmfg.ims.hrm.service.AttendanceService;
import com.springmfg.ims.hrm.service.EmployeeService;
import com.springmfg.ims.hrm.service.LeaveService;
import com.springmfg.ims.hrm.service.PayrollService;
import com.springmfg.ims.hrm.service.ShiftService;
import com.springmfg.ims.hrm.service.SkillService;
import com.springmfg.ims.hrm.service.TrainingService;
import com.springmfg.ims.hrm.web.AttendanceController;
import com.springmfg.ims.hrm.web.EmployeeController;
import com.springmfg.ims.hrm.web.LeaveController;
import com.springmfg.ims.hrm.web.PayrollController;
import com.springmfg.ims.hrm.web.ShiftController;
import com.springmfg.ims.hrm.web.SkillController;
import com.springmfg.ims.hrm.web.TrainingController;

/**
 * 403 matrix for every HR endpoint (R13-5.2). Web slice only, so it runs without Docker. Each endpoint must reject
 * anonymous callers (401), reject callers holding only authorities it does not declare (403) and accept each
 * authority it does declare. A final test fails when a controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { EmployeeController.class, SkillController.class, TrainingController.class,
        ShiftController.class, AttendanceController.class, LeaveController.class, PayrollController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class HrmSecurityMatrixTest {

    private static final String VIEW = "HR_VIEW";
    private static final String EMPLOYEE = "EMPLOYEE_MANAGE";
    private static final String ATTENDANCE = "ATTENDANCE_MANAGE";
    private static final String APPLY = "LEAVE_APPLY";
    private static final String APPROVE = "LEAVE_APPROVE";
    private static final String PAYROLL = "PAYROLL_RUN";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, EMPLOYEE, ATTENDANCE, APPLY, APPROVE, PAYROLL,
            "CRM_VIEW", "SALES_VIEW", "MRP_VIEW", "REPORT_VIEW", "GL_VIEW", "USER_UPDATE");

    private static final String EMPLOYEE_BODY =
            "{\"firstName\":\"Asha\",\"lastName\":\"Rao\",\"dateOfJoining\":\"2024-01-01\",\"employmentType\":\"PERMANENT\"}";
    private static final String DEACTIVATE_BODY = "{\"reason\":\"resigned\"}";
    private static final String SKILL_BODY = "{\"skillType\":\"CERTIFICATION\",\"skillName\":\"Welding\"}";
    private static final String TRAINING_BODY =
            "{\"programmeName\":\"Safety\",\"trainingType\":\"INTERNAL\",\"startDate\":\"2026-01-01\",\"status\":\"PLANNED\"}";
    private static final String SHIFT_BODY =
            "{\"shiftCode\":\"EVE\",\"name\":\"Evening\",\"startTime\":\"14:00:00\",\"endTime\":\"22:00:00\"}";
    private static final String ATTENDANCE_BODY =
            "{\"employeeId\":1,\"attendanceDate\":\"2026-09-01\",\"status\":\"PRESENT\"}";
    private static final String BULK_BODY =
            "{\"entries\":[{\"employeeId\":1,\"attendanceDate\":\"2026-09-01\",\"status\":\"PRESENT\"}]}";
    private static final String LEAVE_BODY = "{\"leaveTypeId\":1,\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-02\"}";
    private static final String DECISION_BODY = "{\"reason\":\"ok\"}";
    private static final String INITIATE_BODY = "{\"month\":9,\"year\":2026}";

    record Endpoint(HttpMethod method, String template, String query, Set<String> authorities, String body) {
        String url() {
            return query == null ? template : template + "?" + query;
        }

        @Override
        public String toString() {
            return method + " " + url() + " " + authorities;
        }
    }

    private static Endpoint ep(HttpMethod m, String template, String query, String body, String... authorities) {
        return new Endpoint(m, template, query, Set.of(authorities), body);
    }

    static List<Endpoint> endpoints() {
        String emp = "/api/hrm/employees";
        String skills = "/api/hrm/skills";
        String training = "/api/hrm/training";
        String shifts = "/api/hrm/shifts";
        String att = "/api/hrm/attendance";
        String leave = "/api/hrm/leave";
        String pay = "/api/hrm/payroll";
        return List.of(
                ep(HttpMethod.GET, emp, null, null, VIEW),
                ep(HttpMethod.GET, emp + "/summaries", null, null, VIEW),
                ep(HttpMethod.GET, emp + "/unlinked-users", null, null, EMPLOYEE),
                ep(HttpMethod.GET, emp + "/{id}", null, null, VIEW),
                ep(HttpMethod.POST, emp, null, EMPLOYEE_BODY, EMPLOYEE),
                ep(HttpMethod.PUT, emp + "/{id}", null, EMPLOYEE_BODY, EMPLOYEE),
                ep(HttpMethod.POST, emp + "/{id}/deactivate", null, DEACTIVATE_BODY, EMPLOYEE),

                ep(HttpMethod.GET, skills + "/employee/{employeeId}", null, null, VIEW),
                ep(HttpMethod.POST, skills + "/employee/{employeeId}", null, SKILL_BODY, EMPLOYEE),
                ep(HttpMethod.PUT, skills + "/employee/{employeeId}/{skillId}", null, SKILL_BODY, EMPLOYEE),
                ep(HttpMethod.DELETE, skills + "/employee/{employeeId}/{skillId}", null, null, EMPLOYEE),
                ep(HttpMethod.GET, skills + "/authorised", "machineId=1", null, VIEW),

                ep(HttpMethod.GET, training + "/employee/{employeeId}", null, null, VIEW),
                ep(HttpMethod.GET, training + "/renewals-due", null, null, VIEW),
                ep(HttpMethod.POST, training + "/employee/{employeeId}", null, TRAINING_BODY, EMPLOYEE),
                ep(HttpMethod.PUT, training + "/employee/{employeeId}/{id}", null, TRAINING_BODY, EMPLOYEE),
                ep(HttpMethod.DELETE, training + "/employee/{employeeId}/{id}", null, null, EMPLOYEE),

                ep(HttpMethod.GET, shifts, null, null, VIEW),
                ep(HttpMethod.POST, shifts, null, SHIFT_BODY, ATTENDANCE),
                ep(HttpMethod.PUT, shifts + "/{id}", null, SHIFT_BODY, ATTENDANCE),

                ep(HttpMethod.GET, att, "date=2026-09-01", null, VIEW),
                ep(HttpMethod.GET, att + "/summary", "year=2026&month=9", null, VIEW),
                ep(HttpMethod.POST, att, null, ATTENDANCE_BODY, ATTENDANCE),
                ep(HttpMethod.POST, att + "/bulk", null, BULK_BODY, ATTENDANCE),

                ep(HttpMethod.GET, leave + "/types", null, null, VIEW, APPLY, APPROVE),
                ep(HttpMethod.GET, leave + "/balances", null, null, VIEW, APPLY, APPROVE),
                ep(HttpMethod.GET, leave + "/applications", null, null, VIEW, APPLY, APPROVE),
                ep(HttpMethod.GET, leave + "/applications/{id}", null, null, VIEW, APPLY, APPROVE),
                ep(HttpMethod.POST, leave + "/applications", null, LEAVE_BODY, APPLY),
                ep(HttpMethod.POST, leave + "/applications/{id}/approve", null, DECISION_BODY, APPROVE),
                ep(HttpMethod.POST, leave + "/applications/{id}/reject", null, DECISION_BODY, APPROVE),
                ep(HttpMethod.POST, leave + "/applications/{id}/cancel", null, DECISION_BODY, APPLY, APPROVE),

                ep(HttpMethod.GET, pay + "/runs", null, null, PAYROLL),
                ep(HttpMethod.GET, pay + "/runs/{id}", null, null, PAYROLL),
                ep(HttpMethod.GET, pay + "/runs/{id}/slips", null, null, PAYROLL),
                ep(HttpMethod.GET, pay + "/slips/{id}", null, null, PAYROLL),
                ep(HttpMethod.POST, pay + "/runs", null, INITIATE_BODY, PAYROLL),
                ep(HttpMethod.POST, pay + "/runs/{id}/calculate", null, null, PAYROLL),
                ep(HttpMethod.POST, pay + "/runs/{id}/approve", null, null, PAYROLL),
                ep(HttpMethod.POST, pay + "/runs/{id}/pay", null, null, PAYROLL));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    static Stream<Object[]> declaredAuthorityStream() {
        return endpoints().stream().flatMap(e -> e.authorities().stream().map(a -> new Object[] { e, a }));
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean EmployeeService employeeService;
    @MockBean SkillService skillService;
    @MockBean TrainingService trainingService;
    @MockBean ShiftService shiftService;
    @MockBean AttendanceService attendanceService;
    @MockBean LeaveService leaveService;
    @MockBean PayrollService payrollService;

    @Autowired MockMvc mvc;

    @BeforeEach
    void stubJwtFilterAsPassThrough() throws Exception {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private ResultActions call(Endpoint e, String authority) throws Exception {
        var builder = request(e.method(), e.url(), 1L, 1L).with(csrf());
        if (authority != null) {
            builder = builder.with(user("tester").authorities(new SimpleGrantedAuthority(authority)));
        }
        if (e.body() != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return mvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} - anonymous gets 401")
    @MethodSource("endpointStream")
    void anonymousIsUnauthorized(Endpoint e) throws Exception {
        call(e, null).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} - any undeclared authority gets 403")
    @MethodSource("endpointStream")
    void undeclaredAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority {1} is accepted")
    @MethodSource("declaredAuthorityStream")
    void declaredAuthorityIsAccepted(Endpoint e, String authority) throws Exception {
        call(e, authority).andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryHrEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(EmployeeController.class, SkillController.class, TrainingController.class,
                ShiftController.class, AttendanceController.class, LeaveController.class, PayrollController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0];
            for (Method m : c.getDeclaredMethods()) {
                String key = key(m, base);
                if (key != null) declared.add(key);
            }
        }
        Set<String> covered = new HashSet<>();
        for (Endpoint e : endpoints()) {
            covered.add(e.method() + " " + e.template().replaceAll("\\{[^}]+}", "{}"));
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(declared);
    }

    private static String key(Method m, String base) {
        String verb = null;
        String[] paths = {};
        if (m.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            paths = m.getAnnotation(GetMapping.class).value();
        } else if (m.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            paths = m.getAnnotation(PostMapping.class).value();
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            paths = m.getAnnotation(PutMapping.class).value();
        } else if (m.isAnnotationPresent(DeleteMapping.class)) {
            verb = "DELETE";
            paths = m.getAnnotation(DeleteMapping.class).value();
        }
        if (verb == null) return null;
        String path = base + (paths.length == 0 ? "" : paths[0]);
        return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}
