package com.springmfg.ims.finance.tax.repository;

import com.springmfg.ims.finance.tax.domain.ProductTaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductTaxSlabRepository extends JpaRepository<ProductTaxSlab, Long> {

    Optional<ProductTaxSlab> findByProductId(Long productId);
}
