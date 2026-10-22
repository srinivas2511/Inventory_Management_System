package com.mfg.ims.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.partner.domain.CustomerApprovedProduct;

public interface CustomerApprovedProductRepository extends JpaRepository<CustomerApprovedProduct, Long> {

    List<CustomerApprovedProduct> findByCustomerId(Long customerId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    void deleteByCustomerId(Long customerId);
}
