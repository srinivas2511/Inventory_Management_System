package com.mfg.ims.mrp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.mrp.domain.MrpPlanningParam;

public interface MrpPlanningParamRepository extends JpaRepository<MrpPlanningParam, Long> {

    Optional<MrpPlanningParam> findByMaterialId(Long materialId);

    Optional<MrpPlanningParam> findByProductId(Long productId);
}
