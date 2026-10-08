package com.springmfg.ims.sales.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.QueryBounds;

import com.springmfg.ims.sales.domain.Dispatch;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {

    List<Dispatch> findBySalesOrderId(Long salesOrderId);

    List<Dispatch> findByCustomerId(Long customerId);

    @Query("SELECT d FROM Dispatch d WHERE " +
           "(:customerId IS NULL OR d.customerId = :customerId) AND " +
           "d.dispatchDate >= :from AND " +
           "d.dispatchDate <= :to AND " +
           "(:scoped = FALSE OR d.plantId IN :plantIds)")
    Page<Dispatch> searchBounded(
            @Param("customerId") Long customerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("scoped") boolean scoped,
            @Param("plantIds") java.util.Collection<Long> plantIds,
            Pageable pageable);

    default Page<Dispatch> searchInPlants(Long customerId, LocalDate from, LocalDate to, boolean scoped,
            java.util.Collection<Long> plantIds, Pageable pageable) {
        return searchBounded(customerId, QueryBounds.from(from), QueryBounds.to(to), scoped, plantIds, pageable);
    }

    default Page<Dispatch> search(Long customerId, LocalDate from, LocalDate to, Pageable pageable) {
        return searchInPlants(customerId, from, to, false, java.util.List.of(-1L), pageable);
    }
}
