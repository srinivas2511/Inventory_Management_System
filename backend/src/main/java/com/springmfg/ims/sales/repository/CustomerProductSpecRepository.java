package com.springmfg.ims.sales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.sales.domain.CustomerProductSpec;

public interface CustomerProductSpecRepository extends JpaRepository<CustomerProductSpec, Long> {

    Optional<CustomerProductSpec> findByCustomerIdAndProductId(Long customerId, Long productId);

    List<CustomerProductSpec> findByCustomerId(Long customerId);

    List<CustomerProductSpec> findByCustomerIdAndActiveTrue(Long customerId);
}
