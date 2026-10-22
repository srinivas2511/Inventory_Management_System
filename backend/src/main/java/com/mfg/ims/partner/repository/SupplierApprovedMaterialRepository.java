package com.mfg.ims.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.partner.domain.SupplierApprovedMaterial;

public interface SupplierApprovedMaterialRepository extends JpaRepository<SupplierApprovedMaterial, Long> {

    List<SupplierApprovedMaterial> findBySupplierId(Long supplierId);

    boolean existsBySupplierIdAndMaterialId(Long supplierId, Long materialId);

    void deleteBySupplierId(Long supplierId);
}
