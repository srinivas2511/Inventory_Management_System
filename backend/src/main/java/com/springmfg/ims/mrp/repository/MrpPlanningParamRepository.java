package com.springmfg.ims.mrp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.mrp.domain.MrpPlanningParam;

public interface MrpPlanningParamRepository extends JpaRepository<MrpPlanningParam, Long> {

    Optional<MrpPlanningParam> findByMaterialId(Long materialId);

    Optional<MrpPlanningParam> findByProductId(Long productId);
}
