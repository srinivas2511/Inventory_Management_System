package com.mfg.ims.quality.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.quality.domain.InspectionPlan;

public interface InspectionPlanRepository extends JpaRepository<InspectionPlan, Long> {

    Optional<InspectionPlan> findByInspectionTypeAndMaterialIdAndActiveTrue(
            String inspectionType, Long materialId);

    Optional<InspectionPlan> findByInspectionTypeAndProductIdAndActiveTrue(
            String inspectionType, Long productId);
}
