package com.mfg.ims.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.product.domain.ProductMaterial;

public interface ProductMaterialRepository extends JpaRepository<ProductMaterial, Long> {

    List<ProductMaterial> findByProductIdOrderByDisplayOrder(Long productId);

    void deleteByProductId(Long productId);
}
