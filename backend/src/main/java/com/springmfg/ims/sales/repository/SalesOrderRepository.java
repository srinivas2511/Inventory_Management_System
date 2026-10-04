package com.springmfg.ims.sales.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.QueryBounds;

import com.springmfg.ims.sales.domain.SalesOrder;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findByStatus(String status);

    List<SalesOrder> findByCustomerId(Long customerId);

    @Query("SELECT o FROM SalesOrder o WHERE " +
           "(:customerId IS NULL OR o.customerId = :customerId) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "o.orderDate >= :from AND " +
           "o.orderDate <= :to")
    Page<SalesOrder> searchBounded(
            @Param("customerId") Long customerId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    default Page<SalesOrder> search(Long customerId, String status, LocalDate from, LocalDate to,
            Pageable pageable) {
        return searchBounded(customerId, status, QueryBounds.from(from), QueryBounds.to(to), pageable);
    }

    @Query("SELECT COUNT(o) FROM SalesOrder o WHERE o.status NOT IN ('CLOSED','CANCELLED')")
    long countOpen();

    @Query("SELECT COUNT(DISTINCT o.id) FROM SalesOrder o JOIN Dispatch d ON d.salesOrderId = o.id " +
           "WHERE d.status = 'DISPATCHED' AND MONTH(d.dispatchDate) = MONTH(CURRENT_DATE) AND YEAR(d.dispatchDate) = YEAR(CURRENT_DATE)")
    long countDispatchedThisMonth();
}
