package com.springmfg.ims.plm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.plm.domain.ProductLifecycleEvent;

public interface ProductLifecycleEventRepository extends JpaRepository<ProductLifecycleEvent, Long> {

    List<ProductLifecycleEvent> findByProductIdOrderByChangedAtDescIdDesc(Long productId);

    List<ProductLifecycleEvent> findByEcoId(Long ecoId);
}
