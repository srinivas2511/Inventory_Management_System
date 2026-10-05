package com.springmfg.ims.maintenance.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.maintenance.domain.SparePart;

public interface SparePartRepository extends JpaRepository<SparePart, Long> {

    boolean existsBySparePartCode(String sparePartCode);

    Optional<SparePart> findByMaterialId(Long materialId);

    /** {@code activeMode}: 0 = all, 1 = active only, 2 = inactive only (an int, so PostgreSQL never sees an untyped null). */
    @Query("SELECT s FROM SparePart s WHERE "
            + "(:q = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "   OR LOWER(s.sparePartCode) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:activeMode = 0 OR (:activeMode = 1 AND s.active = true) OR (:activeMode = 2 AND s.active = false))")
    Page<SparePart> searchNormalised(@Param("q") String q, @Param("activeMode") int activeMode, Pageable pageable);

    default Page<SparePart> search(String q, Boolean active, Pageable pageable) {
        int mode = active == null ? 0 : (active ? 1 : 2);
        return searchNormalised(SearchText.normalise(q), mode, pageable);
    }
}
