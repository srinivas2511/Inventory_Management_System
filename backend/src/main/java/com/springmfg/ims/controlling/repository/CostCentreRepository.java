package com.springmfg.ims.controlling.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.controlling.domain.CostCentre;

public interface CostCentreRepository extends JpaRepository<CostCentre, Long> {

    Optional<CostCentre> findByCostCentreCode(String costCentreCode);

    boolean existsByCostCentreCode(String costCentreCode);

    boolean existsByParentIdAndActiveTrue(Long parentId);

    List<CostCentre> findAllByOrderByCostCentreCode();
}
