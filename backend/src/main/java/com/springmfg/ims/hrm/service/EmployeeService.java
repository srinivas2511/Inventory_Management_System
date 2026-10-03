package com.springmfg.ims.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.enterprise.repository.PlantRepository;
import com.springmfg.ims.hrm.domain.Employee;
import com.springmfg.ims.hrm.dto.EmployeeDtos.DeactivateEmployeeRequest;
import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeDto;
import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeSummaryDto;
import com.springmfg.ims.hrm.dto.EmployeeDtos.SaveEmployeeRequest;
import com.springmfg.ims.hrm.dto.EmployeeDtos.UnlinkedUserDto;
import com.springmfg.ims.hrm.repository.EmployeeRepository;
import com.springmfg.ims.hrm.repository.ShiftRepository;
import com.springmfg.ims.iam.domain.Role;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class EmployeeService {

    /** IAM roles whose holders must be linked to an employee record (MDM-041). */
    static final List<String> SHOP_FLOOR_ROLES = List.of("OPERATOR", "SUPERVISOR");

    private final EmployeeRepository repo;
    private final UserRepository userRepo;
    private final ShiftRepository shiftRepo;
    private final PlantRepository plantRepo;
    private final CostCentreRepository costCentreRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public EmployeeService(EmployeeRepository repo, UserRepository userRepo, ShiftRepository shiftRepo,
                           PlantRepository plantRepo, CostCentreRepository costCentreRepo,
                           DocumentNumberService numberService, AuditService auditService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.shiftRepo = shiftRepo;
        this.plantRepo = plantRepo;
        this.costCentreRepo = costCentreRepo;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDto> search(String q, String department, Boolean active, Pageable pageable) {
        String text = SearchText.normalise(q).toLowerCase();
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!text.isEmpty()) {
                String like = "%" + text + "%";
                p.add(cb.or(cb.like(cb.lower(root.<String>get("employeeCode")), like),
                        cb.like(cb.lower(root.<String>get("firstName")), like),
                        cb.like(cb.lower(root.<String>get("lastName")), like)));
            }
            if (department != null && !department.isBlank()) {
                p.add(cb.equal(root.get("department"), department.trim()));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        boolean visible = HrmSecurity.canSeeCompensation();
        return repo.findAll(spec, pageable).map(e -> toDto(e, visible));
    }

    @Transactional(readOnly = true)
    public EmployeeDto findById(Long id) {
        return toDto(require(id), HrmSecurity.canSeeCompensation());
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryDto> activeSummaries() {
        return repo.findByActiveTrueOrderByEmployeeCode().stream().map(EmployeeService::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<UnlinkedUserDto> unlinkedShopFloorUsers() {
        return repo.findUsersWithoutEmployee(SHOP_FLOOR_ROLES).stream()
                .map(u -> new UnlinkedUserDto(u.getId(), u.getUsername(), u.getFullName(),
                        u.getRoles().stream().map(Role::getCode).sorted().collect(Collectors.joining(","))))
                .toList();
    }

    @Transactional
    public EmployeeDto create(SaveEmployeeRequest req) {
        Employee e = new Employee();
        String code = req.employeeCode() == null || req.employeeCode().isBlank()
                ? numberService.next("EMP") : req.employeeCode().trim();
        if (repo.existsByEmployeeCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Employee code " + code + " already exists");
        }
        e.setEmployeeCode(code);
        validateLinks(req, null);
        apply(e, req);
        e.setBasicPay(nonNull(req.basicPay()));
        e.setFixedTravelAllowance(nonNull(req.fixedTravelAllowance()));
        e.setFixedOtherAllowances(nonNull(req.fixedOtherAllowances()));
        e = repo.save(e);
        auditService.record("EMPLOYEE_CREATE", "EMPLOYEE", String.valueOf(e.getId()), null, e.getEmployeeCode(), null);
        return toDto(e, HrmSecurity.canSeeCompensation());
    }

    @Transactional
    public EmployeeDto update(Long id, SaveEmployeeRequest req) {
        Employee e = require(id);
        if (req.version() != null && !req.version().equals(e.getVersion())) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "Employee was changed by someone else");
        }
        validateLinks(req, e);

        String oldPay = payFingerprint(e);
        BigDecimal basic = req.basicPay() != null ? req.basicPay() : e.getBasicPay();
        BigDecimal travel = req.fixedTravelAllowance() != null ? req.fixedTravelAllowance() : e.getFixedTravelAllowance();
        BigDecimal other = req.fixedOtherAllowances() != null ? req.fixedOtherAllowances() : e.getFixedOtherAllowances();
        boolean payChanged = basic.compareTo(e.getBasicPay()) != 0 || travel.compareTo(e.getFixedTravelAllowance()) != 0
                || other.compareTo(e.getFixedOtherAllowances()) != 0;
        if (payChanged && (req.payChangeReason() == null || req.payChangeReason().isBlank())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A reason is required when pay is changed (payChangeReason)");
        }

        apply(e, req);
        e.setBasicPay(basic);
        e.setFixedTravelAllowance(travel);
        e.setFixedOtherAllowances(other);
        e = repo.save(e);

        if (payChanged) {
            auditService.record("EMPLOYEE_PAY_CHANGE", "EMPLOYEE", String.valueOf(id), oldPay, payFingerprint(e),
                    req.payChangeReason());
        }
        auditService.record("EMPLOYEE_UPDATE", "EMPLOYEE", String.valueOf(id), null, e.getEmployeeCode(), null);
        return toDto(e, HrmSecurity.canSeeCompensation());
    }

    @Transactional
    public EmployeeDto deactivate(Long id, DeactivateEmployeeRequest req) {
        Employee e = require(id);
        if (!e.isActive()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Employee is already inactive");
        }
        LocalDate leaving = req.dateOfLeaving() != null ? req.dateOfLeaving() : LocalDate.now();
        if (leaving.isBefore(e.getDateOfJoining())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Leaving date is before the joining date");
        }
        e.setActive(false);
        e.setDateOfLeaving(leaving);
        e = repo.save(e);
        auditService.record("EMPLOYEE_DEACTIVATE", "EMPLOYEE", String.valueOf(id), "ACTIVE", "INACTIVE", req.reason());
        return toDto(e, HrmSecurity.canSeeCompensation());
    }

    Employee require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Employee", id));
    }

    static EmployeeSummaryDto summary(Employee e) {
        return new EmployeeSummaryDto(e.getId(), e.getEmployeeCode(), e.fullName(), e.getDesignation(),
                e.getDepartment());
    }

    EmployeeDto toDto(Employee e, boolean compensationVisible) {
        String username = e.getUserId() == null ? null
                : userRepo.findById(e.getUserId()).map(User::getUsername).orElse(null);
        return new EmployeeDto(e.getId(), e.getEmployeeCode(), e.getUserId(), username, e.getFirstName(),
                e.getLastName(), e.fullName(), e.getEmail(), e.getPhone(), e.getDesignation(), e.getDepartment(),
                e.getPlantId(), e.getCostCentreId(), e.getDateOfJoining(), e.getDateOfLeaving(),
                e.getEmploymentType(), e.getPayGrade(), e.getDefaultShift(), e.isActive(), compensationVisible,
                compensationVisible ? e.getBasicPay() : null,
                compensationVisible ? e.getFixedTravelAllowance() : null,
                compensationVisible ? e.getFixedOtherAllowances() : null,
                compensationVisible ? e.getBankAccountNo() : null,
                compensationVisible ? e.getBankIfsc() : null,
                compensationVisible ? e.getPfNumber() : null,
                compensationVisible ? e.getEsiNumber() : null,
                e.getVersion());
    }

    private void apply(Employee e, SaveEmployeeRequest req) {
        e.setUserId(req.userId());
        e.setFirstName(req.firstName().trim());
        e.setLastName(req.lastName().trim());
        e.setEmail(blankToNull(req.email()));
        e.setPhone(blankToNull(req.phone()));
        e.setDesignation(blankToNull(req.designation()));
        e.setDepartment(blankToNull(req.department()));
        e.setPlantId(req.plantId());
        e.setCostCentreId(req.costCentreId());
        e.setDateOfJoining(req.dateOfJoining());
        e.setEmploymentType(req.employmentType().name());
        e.setPayGrade(blankToNull(req.payGrade()));
        e.setBankAccountNo(blankToNull(req.bankAccountNo()));
        e.setBankIfsc(blankToNull(req.bankIfsc()));
        e.setPfNumber(blankToNull(req.pfNumber()));
        e.setEsiNumber(blankToNull(req.esiNumber()));
        e.setDefaultShift(blankToNull(req.defaultShift()));
    }

    private void validateLinks(SaveEmployeeRequest req, Employee existing) {
        if (req.userId() != null) {
            if (!userRepo.existsById(req.userId())) {
                throw new NotFoundException("User", req.userId());
            }
            repo.findByUserId(req.userId()).ifPresent(other -> {
                if (existing == null || !other.getId().equals(existing.getId())) {
                    throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                            "That user is already linked to employee " + other.getEmployeeCode());
                }
            });
        }
        if (req.plantId() != null && !plantRepo.existsById(req.plantId())) {
            throw new NotFoundException("Plant", req.plantId());
        }
        if (req.costCentreId() != null && !costCentreRepo.existsById(req.costCentreId())) {
            throw new NotFoundException("CostCentre", req.costCentreId());
        }
        if (req.defaultShift() != null && !req.defaultShift().isBlank()
                && shiftRepo.findByShiftCode(req.defaultShift().trim()).isEmpty()) {
            throw new NotFoundException("Shift", req.defaultShift());
        }
    }

    private static String payFingerprint(Employee e) {
        return "basic=" + e.getBasicPay().toPlainString() + ";travel=" + e.getFixedTravelAllowance().toPlainString()
                + ";other=" + e.getFixedOtherAllowances().toPlainString();
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
