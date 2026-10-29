package com.mfg.ims.production.repository;

import com.mfg.ims.production.domain.ProductionOrderTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductionOrderTemplateRepository extends JpaRepository<ProductionOrderTemplate, Long> {
    List<ProductionOrderTemplate> findByCompanyIdAndActiveOrderByNameAsc(Long companyId, boolean active);
    Optional<ProductionOrderTemplate> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
