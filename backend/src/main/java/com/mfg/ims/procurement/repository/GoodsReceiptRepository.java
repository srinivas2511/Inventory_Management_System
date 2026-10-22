package com.mfg.ims.procurement.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.common.search.QueryBounds;
import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.procurement.domain.GoodsReceipt;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    Optional<GoodsReceipt> findByGrNumber(String grNumber);

    Page<GoodsReceipt> findByPurchaseOrderId(Long poId, Pageable pageable);

    @Query("SELECT g FROM GoodsReceipt g WHERE " +
           "(:status = '' OR g.status = :status) AND " +
           "(:poId IS NULL OR g.purchaseOrderId = :poId) AND " +
           "(:po = '' OR g.purchaseOrderId IN (SELECT p.id FROM PurchaseOrder p " +
           "   WHERE LOWER(p.poNumber) LIKE LOWER(CONCAT('%', :po, '%')))) AND " +
           "g.receiptDate >= :from AND g.receiptDate <= :to AND " +
           "(:scoped = FALSE OR g.plantId IN :plantIds)")
    Page<GoodsReceipt> searchBounded(@Param("status") String status, @Param("poId") Long purchaseOrderId,
                                     @Param("po") String poNumber, @Param("from") LocalDate from,
                                     @Param("to") LocalDate to, @Param("scoped") boolean scoped,
                                     @Param("plantIds") java.util.Collection<Long> plantIds, Pageable pageable);

    default Page<GoodsReceipt> searchInPlants(String status, Long purchaseOrderId, String poNumber,
                                              LocalDate from, LocalDate to, boolean scoped,
                                              java.util.Collection<Long> plantIds, Pageable pageable) {
        return searchBounded(SearchText.normalise(status), purchaseOrderId, SearchText.normalise(poNumber),
                QueryBounds.from(from), QueryBounds.to(to), scoped, plantIds, pageable);
    }

    default Page<GoodsReceipt> search(String status, Long purchaseOrderId, String poNumber,
                                      LocalDate from, LocalDate to, Pageable pageable) {
        return searchInPlants(status, purchaseOrderId, poNumber, from, to, false, java.util.List.of(-1L), pageable);
    }
}
