package com.springmfg.ims.controlling.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.controlling.domain.ProfitCentre;

public interface ProfitCentreRepository extends JpaRepository<ProfitCentre, Long> {

    boolean existsByProfitCentreCode(String profitCentreCode);

    List<ProfitCentre> findAllByOrderByProfitCentreCode();
}
