package com.springmfg.ims.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.partner.domain.SupplierApprovedMaterial;

public interface SupplierApprovedMaterialRepository extends JpaRepository<SupplierApprovedMaterial, Long> {

    List<SupplierApprovedMaterial> findBySupplierId(Long supplierId);

    boolean existsBySupplierIdAndMaterialId(Long supplierId, Long materialId);

    void deleteBySupplierId(Long supplierId);
}
