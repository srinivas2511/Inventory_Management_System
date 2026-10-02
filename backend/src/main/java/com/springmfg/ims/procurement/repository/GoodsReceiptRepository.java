package com.springmfg.ims.procurement.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.procurement.domain.GoodsReceipt;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    Optional<GoodsReceipt> findByGrNumber(String grNumber);

    Page<GoodsReceipt> findByPurchaseOrderId(Long poId, Pageable pageable);
}
