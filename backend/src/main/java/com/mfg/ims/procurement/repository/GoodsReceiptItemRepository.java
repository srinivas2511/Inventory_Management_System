package com.mfg.ims.procurement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.procurement.domain.GoodsReceiptItem;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {

    List<GoodsReceiptItem> findByGoodsReceiptId(Long goodsReceiptId);
}
