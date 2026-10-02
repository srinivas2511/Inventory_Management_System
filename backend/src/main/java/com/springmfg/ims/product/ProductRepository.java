package com.springmfg.ims.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByProductCode(String productCode);

    /** Lists load the material and customer in the same query. */
    @Override
    @EntityGraph(attributePaths = { "primaryMaterial", "customer" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}
