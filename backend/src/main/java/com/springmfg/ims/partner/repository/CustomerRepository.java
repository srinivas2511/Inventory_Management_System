package com.springmfg.ims.partner.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.partner.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCustomerCode(String customerCode);

    @Query("SELECT c FROM Customer c WHERE " +
           "(:q IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR c.status = :status)")
    Page<Customer> search(@Param("q") String q,
                          @Param("status") String status,
                          Pageable pageable);
}
