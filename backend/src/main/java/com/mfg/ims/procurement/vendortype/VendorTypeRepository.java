package com.mfg.ims.procurement.vendortype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorTypeRepository extends JpaRepository<VendorType, Long> {
    boolean existsByCode(String code);
    List<VendorType> findAllByActiveTrueOrderByCode();
    List<VendorType> findAllByOrderByCode();
}
