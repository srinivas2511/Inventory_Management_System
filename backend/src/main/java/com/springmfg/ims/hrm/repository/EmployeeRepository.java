package com.springmfg.ims.hrm.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.hrm.domain.Employee;
import com.springmfg.ims.iam.domain.User;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByUserId(Long userId);

    boolean existsByEmployeeCode(String employeeCode);

    List<Employee> findByActiveTrueOrderByEmployeeCode();

    /** Everyone employed for any part of the month: joined by month end and not left before month start. */
    @Query("select e from Employee e where e.dateOfJoining <= :monthEnd "
            + "and ((e.dateOfLeaving is null and e.active = true) or e.dateOfLeaving >= :monthStart) "
            + "order by e.employeeCode")
    List<Employee> findEmployedDuring(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query("select u from User u join u.roles r where r.code in :roleCodes "
            + "and not exists (select e.id from Employee e where e.userId = u.id) order by u.username")
    List<User> findUsersWithoutEmployee(@Param("roleCodes") Collection<String> roleCodes);
}
