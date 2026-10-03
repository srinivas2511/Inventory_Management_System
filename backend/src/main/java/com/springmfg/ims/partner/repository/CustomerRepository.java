package com.springmfg.ims.partner.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.partner.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCustomerCode(String customerCode);

    Optional<Customer> findByCustomerCode(String customerCode);

    @Query("SELECT c FROM Customer c WHERE " +
           "(:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR c.status = :status)")
    Page<Customer> searchNormalised(@Param("q") String q,
                          @Param("status") String status,
                          Pageable pageable);

    default Page<Customer> search(String q, String status, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), status, pageable);
    }
}
