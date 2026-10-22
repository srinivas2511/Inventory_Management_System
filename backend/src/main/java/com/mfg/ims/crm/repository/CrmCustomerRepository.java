package com.mfg.ims.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.partner.domain.Customer;

/** CRM's tag-aware view over the customer master; the partner module's own repository is untouched. */
public interface CrmCustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Parameters are never null (PostgreSQL cannot type a null string inside LOWER): an empty {@code q} means no text
     * filter and a {@code tagId} of 0 means no tag filter.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE (:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:tagId = 0 OR EXISTS (
                    SELECT 1 FROM CustomerTagAssignment a WHERE a.customerId = c.id AND a.tagId = :tagId))
            """)
    Page<Customer> searchWithTag(@Param("q") String q, @Param("tagId") long tagId, Pageable pageable);
}
