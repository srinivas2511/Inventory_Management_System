package com.mfg.ims.material.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.material.domain.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    boolean existsByMaterialCode(String materialCode);

    Optional<Material> findByMaterialCode(String materialCode);

    java.util.List<Material> findByAllowNegativeStockTrueOrderByMaterialCode();

    List<Material> findAllByActiveTrue();

    @Query("SELECT m FROM Material m WHERE " +
           "(:q = '' OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(m.materialCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:type IS NULL OR m.materialType = :type) " +
           "AND (:active IS NULL OR m.active = :active)")
    Page<Material> searchNormalised(@Param("q") String q,
                          @Param("type") String type,
                          @Param("active") Boolean active,
                          Pageable pageable);

    default Page<Material> search(String q, String type, Boolean active, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), type, active, pageable);
    }
}
