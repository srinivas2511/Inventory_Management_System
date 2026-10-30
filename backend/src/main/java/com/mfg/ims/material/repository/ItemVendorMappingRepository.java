package com.mfg.ims.material.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.material.domain.ItemVendorMapping;

public interface ItemVendorMappingRepository extends JpaRepository<ItemVendorMapping, Long> {

    List<ItemVendorMapping> findByItemIdOrderByPreferredDescSupplierId(Long itemId);

    Optional<ItemVendorMapping> findByItemIdAndSupplierId(Long itemId, Long supplierId);

    boolean existsByItemIdAndSupplierId(Long itemId, Long supplierId);
}
