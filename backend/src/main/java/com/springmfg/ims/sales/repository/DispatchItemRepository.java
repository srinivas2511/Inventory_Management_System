package com.springmfg.ims.sales.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.sales.domain.DispatchItem;

public interface DispatchItemRepository extends JpaRepository<DispatchItem, Long> {

    List<DispatchItem> findByDispatchId(Long dispatchId);

    List<DispatchItem> findBySalesOrderItemId(Long salesOrderItemId);
}
