package com.springmfg.ims.enterprise.repository;

import com.springmfg.ims.enterprise.domain.Plant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantRepository extends JpaRepository<Plant, Long> {
    Optional<Plant> findByPlantCode(String plantCode);
    List<Plant> findByCompanyId(Long companyId);
}
