package com.mfg.ims.doccore.itemcategory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {
    List<ItemCategory> findByCompanyIdAndActiveTrueOrderByCategoryCode(Long companyId);
    Optional<ItemCategory> findByCompanyIdAndCategoryCode(Long companyId, String categoryCode);
    boolean existsByCompanyIdAndCategoryCode(Long companyId, String categoryCode);
}
