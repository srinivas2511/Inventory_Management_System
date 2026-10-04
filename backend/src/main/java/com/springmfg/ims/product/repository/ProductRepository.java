package com.springmfg.ims.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    Optional<Product> findByProductCode(String productCode);

    @Query("SELECT p FROM Product p WHERE " +
           "(:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:springType IS NULL OR p.springType = :springType) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:active IS NULL OR p.active = :active)")
    Page<Product> searchNormalised(@Param("q") String q,
                         @Param("springType") String springType,
                         @Param("status") String status,
                         @Param("active") Boolean active,
                         Pageable pageable);

    default Page<Product> search(String q, String springType, String status, Boolean active, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), springType, status, active, pageable);
    }

    @Query("SELECT p FROM Product p WHERE " +
           "(:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:lifecycle = '' OR p.lifecycleStatus = :lifecycle)")
    Page<Product> searchByLifecycleNormalised(@Param("q") String q, @Param("lifecycle") String lifecycle,
                                              Pageable pageable);

    default Page<Product> searchByLifecycle(String q, String lifecycle, Pageable pageable) {
        return searchByLifecycleNormalised(SearchText.normalise(q), SearchText.normalise(lifecycle), pageable);
    }
}
