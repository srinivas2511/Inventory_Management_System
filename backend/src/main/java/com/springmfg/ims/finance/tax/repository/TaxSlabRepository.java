package com.springmfg.ims.finance.tax.repository;

import com.springmfg.ims.finance.tax.domain.TaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxSlabRepository extends JpaRepository<TaxSlab, Long> {

    List<TaxSlab> findBySlabCodeAndSupplyTypeAndActiveTrue(String slabCode, String supplyType);

    List<TaxSlab> findByActiveTrueOrderBySlabCodeAscSupplyTypeAscIdAsc();

    boolean existsBySlabCodeAndActiveTrue(String slabCode);
}
