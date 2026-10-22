package com.mfg.ims.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.product.domain.ProductType;

public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    List<ProductType> findByActiveTrueOrderByDisplayOrderAscCodeAsc();

    boolean existsByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);
}
