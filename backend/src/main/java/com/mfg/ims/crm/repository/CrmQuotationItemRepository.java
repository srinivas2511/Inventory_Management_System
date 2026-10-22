package com.mfg.ims.crm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.crm.domain.CrmQuotationItem;

public interface CrmQuotationItemRepository extends JpaRepository<CrmQuotationItem, Long> {

    List<CrmQuotationItem> findByQuotationIdOrderByLineNo(Long quotationId);

    /** Bulk delete runs immediately, so replacement lines with the same line numbers do not hit the unique key. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM CrmQuotationItem i WHERE i.quotationId = :quotationId")
    void deleteAllByQuotationId(@Param("quotationId") Long quotationId);
}
