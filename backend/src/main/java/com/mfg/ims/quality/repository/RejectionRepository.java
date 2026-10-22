package com.mfg.ims.quality.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.quality.domain.Rejection;

public interface RejectionRepository extends JpaRepository<Rejection, Long> {

    List<Rejection> findByInspectionId(Long inspectionId);

    @Query("SELECT r FROM Rejection r WHERE (:productId IS NULL OR r.productId = :productId) " +
           "AND (:reasonId IS NULL OR r.rejectionReasonId = :reasonId)")
    Page<Rejection> search(@Param("productId") Long productId,
                           @Param("reasonId") Long reasonId, Pageable pageable);

    @Query("SELECT r.rejectionReasonId, COUNT(r), SUM(r.quantity) FROM Rejection r " +
           "WHERE r.itemType = 'PRODUCT' " +
           "GROUP BY r.rejectionReasonId ORDER BY SUM(r.quantity) DESC")
    List<Object[]> paretoByReason();

    @Query(value = """
        SELECT DATE_TRUNC('month', r.created_at) AS month,
               SUM(r.quantity) AS rejected_qty
        FROM ims.rejections r
        WHERE r.item_type = 'PRODUCT'
          AND r.created_at >= CURRENT_DATE - INTERVAL '12 months'
        GROUP BY month ORDER BY month
        """, nativeQuery = true)
    List<Object[]> rejectionTrend();

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
