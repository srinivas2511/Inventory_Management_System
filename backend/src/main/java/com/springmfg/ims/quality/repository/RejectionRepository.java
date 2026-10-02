package com.springmfg.ims.quality.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.quality.domain.Rejection;

public interface RejectionRepository extends JpaRepository<Rejection, Long> {

    List<Rejection> findByInspectionId(Long inspectionId);

    /** Pareto by reason: count and total quantity per reason for supplier performance queries. */
    @Query("SELECT r.rejectionReasonId, COUNT(r), SUM(r.quantity) FROM Rejection r " +
           "WHERE r.itemType = :itemType GROUP BY r.rejectionReasonId ORDER BY COUNT(r) DESC")
    List<Object[]> paretoBySeason(@Param("itemType") String itemType);

    /** Supplier acceptance rate: total received vs accepted. */
    @Query(value = """
        SELECT poi.material_id,
               SUM(poi.received_qty) AS received,
               SUM(poi.accepted_qty) AS accepted,
               SUM(poi.rejected_qty) AS rejected
        FROM ims.purchase_order_items poi
        JOIN ims.purchase_orders po ON po.id = poi.purchase_order_id
        WHERE po.supplier_id = :supplierId AND po.status IN ('PARTIALLY_RECEIVED','RECEIVED')
        GROUP BY poi.material_id
        """, nativeQuery = true)
    List<Object[]> supplierAcceptanceByMaterial(@Param("supplierId") Long supplierId);
}
