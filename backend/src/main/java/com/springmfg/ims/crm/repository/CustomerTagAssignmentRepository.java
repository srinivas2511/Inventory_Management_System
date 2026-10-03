package com.springmfg.ims.crm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.crm.domain.CustomerTagAssignment;

public interface CustomerTagAssignmentRepository extends JpaRepository<CustomerTagAssignment, Long> {

    List<CustomerTagAssignment> findByCustomerId(Long customerId);

    List<CustomerTagAssignment> findByCustomerIdIn(Collection<Long> customerIds);

    boolean existsByTagId(Long tagId);

    void deleteByCustomerId(Long customerId);
}
