package com.mfg.ims.warehouse.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.warehouse.domain.Warehouse;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByWarehouseCode(String code);

    @Query("SELECT w.id FROM Warehouse w WHERE w.plantId IN :plantIds")
    List<Long> findIdsByPlantIdIn(@Param("plantIds") java.util.Collection<Long> plantIds);

    List<Warehouse> findByActiveTrue();

    @Query("SELECT w FROM Warehouse w WHERE (:q = '' OR LOWER(w.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(w.warehouseCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:active IS NULL OR w.active = :active)")
    List<Warehouse> searchNormalised(@Param("q") String q, @Param("active") Boolean active);

    default List<Warehouse> search(String q, Boolean active) {
        return searchNormalised(SearchText.normalise(q), active);
    }
}
