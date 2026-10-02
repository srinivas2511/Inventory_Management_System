package com.springmfg.ims.material.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.material.domain.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    boolean existsByMaterialCode(String materialCode);

    @Query("SELECT m FROM Material m WHERE " +
           "(:q IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(m.materialCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:type IS NULL OR m.materialType = :type) " +
           "AND (:active IS NULL OR m.active = :active)")
    Page<Material> search(@Param("q") String q,
                          @Param("type") String type,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
