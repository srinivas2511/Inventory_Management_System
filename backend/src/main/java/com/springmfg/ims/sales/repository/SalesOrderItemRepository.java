package com.springmfg.ims.sales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.sales.domain.SalesOrderItem;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    List<SalesOrderItem> findBySalesOrderId(Long salesOrderId);

    @Query("SELECT i FROM SalesOrderItem i WHERE i.salesOrderId = :soId AND i.productId = :productId")
    Optional<SalesOrderItem> findBySalesOrderIdAndProductId(
            @Param("soId") Long salesOrderId, @Param("productId") Long productId);

    @Query("SELECT COALESCE(MAX(i.lineNo), 0) FROM SalesOrderItem i WHERE i.salesOrderId = :soId")
    int maxLineNo(@Param("soId") Long salesOrderId);
}
