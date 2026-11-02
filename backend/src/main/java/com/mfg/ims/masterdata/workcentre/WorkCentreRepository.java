package com.mfg.ims.masterdata.workcentre;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkCentreRepository extends JpaRepository<WorkCentre, Long> {
    boolean existsByCode(String code);
    List<WorkCentre> findAllByActiveTrueOrderByCode();
    List<WorkCentre> findAllByOrderByCode();
}
