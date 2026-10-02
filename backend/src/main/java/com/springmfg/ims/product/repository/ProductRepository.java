package com.springmfg.ims.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    @Query("SELECT p FROM Product p WHERE " +
           "(:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:springType IS NULL OR p.springType = :springType) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:active IS NULL OR p.active = :active)")
    Page<Product> search(@Param("q") String q,
                         @Param("springType") String springType,
                         @Param("status") String status,
                         @Param("active") Boolean active,
                         Pageable pageable);
}
