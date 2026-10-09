package com.springmfg.ims.material.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.material.domain.UomConversion;

public interface UomConversionRepository extends JpaRepository<UomConversion, Long> {

    List<UomConversion> findByMaterialIdOrderByAltUom(Long materialId);

    Optional<UomConversion> findByMaterialIdAndAltUom(Long materialId, String altUom);
}
