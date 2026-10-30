package com.mfg.ims.material.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.ItemVendorMapping;
import com.mfg.ims.material.dto.ItemVendorMappingDto;
import com.mfg.ims.material.repository.ItemVendorMappingRepository;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.partner.repository.SupplierRepository;

@Service
public class ItemVendorMappingService {

    private final ItemVendorMappingRepository repo;
    private final MaterialRepository materialRepo;
    private final SupplierRepository supplierRepo;

    public ItemVendorMappingService(ItemVendorMappingRepository repo,
                                    MaterialRepository materialRepo,
                                    SupplierRepository supplierRepo) {
        this.repo = repo;
        this.materialRepo = materialRepo;
        this.supplierRepo = supplierRepo;
    }

    @Transactional(readOnly = true)
    public List<ItemVendorMappingDto> listByItem(Long itemId) {
        materialRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Material not found: " + itemId));
        return repo.findByItemIdOrderByPreferredDescSupplierId(itemId)
                .stream()
                .map(m -> {
                    String supplierName = supplierRepo.findById(m.getSupplierId())
                            .map(s -> s.getName()).orElse("Unknown");
                    return ItemVendorMappingDto.from(m, supplierName);
                })
                .toList();
    }

    @Transactional
    public ItemVendorMappingDto create(Long itemId, Long supplierId, String supplierItemCode,
                                       String supplierItemName, BigDecimal lastPrice,
                                       BigDecimal moq, int leadTimeDays, boolean preferred) {
        materialRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Material not found: " + itemId));
        var supplier = supplierRepo.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + supplierId));
        if (repo.existsByItemIdAndSupplierId(itemId, supplierId)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Mapping already exists for this material and supplier");
        }
        ItemVendorMapping m = new ItemVendorMapping();
        m.setItemId(itemId);
        m.setSupplierId(supplierId);
        m.setSupplierItemCode(supplierItemCode);
        m.setSupplierItemName(supplierItemName);
        m.setLastPrice(lastPrice);
        m.setMoq(moq != null ? moq : BigDecimal.ZERO);
        m.setLeadTimeDays(leadTimeDays);
        m.setPreferred(preferred);
        m = repo.save(m);
        return ItemVendorMappingDto.from(m, supplier.getName());
    }

    @Transactional
    public ItemVendorMappingDto update(Long mappingId, String supplierItemCode,
                                        String supplierItemName, BigDecimal lastPrice,
                                        BigDecimal moq, int leadTimeDays, boolean preferred) {
        ItemVendorMapping m = repo.findById(mappingId)
                .orElseThrow(() -> new NotFoundException("Mapping not found: " + mappingId));
        m.setSupplierItemCode(supplierItemCode);
        m.setSupplierItemName(supplierItemName);
        m.setLastPrice(lastPrice);
        m.setMoq(moq != null ? moq : BigDecimal.ZERO);
        m.setLeadTimeDays(leadTimeDays);
        m.setPreferred(preferred);
        m = repo.save(m);
        String supplierName = supplierRepo.findById(m.getSupplierId())
                .map(s -> s.getName()).orElse("Unknown");
        return ItemVendorMappingDto.from(m, supplierName);
    }

    @Transactional
    public void delete(Long mappingId) {
        if (!repo.existsById(mappingId)) {
            throw new NotFoundException("Mapping not found: " + mappingId);
        }
        repo.deleteById(mappingId);
    }
}
