package com.springmfg.ims.quality.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.quality.domain.QualityInspectionItem;

public interface QualityInspectionItemRepository extends JpaRepository<QualityInspectionItem, Long> {

    List<QualityInspectionItem> findByInspectionId(Long inspectionId);
}
