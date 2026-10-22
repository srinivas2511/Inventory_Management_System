package com.mfg.ims.engineering.routing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRoutingRepository extends JpaRepository<ProductRouting, Long> {

    List<ProductRouting> findByProductIdOrderBySequenceNo(Long productId);

    List<ProductRouting> findByProductIdAndActiveTrue(Long productId);
}
