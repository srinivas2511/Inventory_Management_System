package com.mfg.ims.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.inventory.domain.FgBatch;
import com.mfg.ims.inventory.domain.MaterialBatch;
import com.mfg.ims.inventory.dto.MaterialBatchDto;
import com.mfg.ims.inventory.repository.FgBatchRepository;
import com.mfg.ims.inventory.repository.MaterialBatchRepository;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.partner.repository.SupplierRepository;
import com.mfg.ims.product.repository.ProductRepository;

@Service
public class BatchService {

    private final MaterialBatchRepository batchRepo;
    private final FgBatchRepository fgBatchRepo;
    private final MaterialRepository materialRepo;
    private final SupplierRepository supplierRepo;
    private final ProductRepository productRepo;
    private final DocumentNumberService numberService;

    public BatchService(MaterialBatchRepository batchRepo,
                        FgBatchRepository fgBatchRepo,
                        MaterialRepository materialRepo,
                        SupplierRepository supplierRepo,
                        ProductRepository productRepo,
                        DocumentNumberService numberService) {
        this.batchRepo = batchRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.materialRepo = materialRepo;
        this.supplierRepo = supplierRepo;
        this.productRepo = productRepo;
        this.numberService = numberService;
    }

    @Transactional
    public MaterialBatch createMaterialBatch(Long materialId, Long supplierId,
                                             Long purchaseOrderId, Long grItemId,
                                             LocalDate manufacturingDate, LocalDate receivedDate,
                                             LocalDate expiryDate, BigDecimal qty,
                                             String heatNumber, String certificateNumber,
                                             BigDecimal unitCost, String initialStatus) {
        MaterialBatch batch = new MaterialBatch();
        batch.setBatchNumber(numberService.next("RM-BATCH"));
        batch.setMaterial(materialRepo.findById(materialId)
                .orElseThrow(() -> new NotFoundException("Material not found: " + materialId)));
        if (supplierId != null) {
            batch.setSupplier(supplierRepo.findById(supplierId).orElse(null));
        }
        batch.setPurchaseOrderId(purchaseOrderId);
        batch.setGoodsReceiptItemId(grItemId);
        batch.setManufacturingDate(manufacturingDate);
        batch.setReceivedDate(receivedDate != null ? receivedDate : LocalDate.now());
        batch.setExpiryDate(expiryDate);
        batch.setReceivedQty(qty);
        batch.setRemainingQty(qty);
        batch.setHeatNumber(heatNumber);
        batch.setCertificateNumber(certificateNumber);
        batch.setUnitCost(unitCost);
        batch.setStatus(initialStatus != null ? initialStatus : "QUARANTINE");
        return batchRepo.save(batch);
    }

    @Transactional
    public FgBatch createFgBatch(Long productId, Long productionOrderId,
                                 BigDecimal producedQty, LocalDate producedOn, BigDecimal unitCost) {
        FgBatch batch = new FgBatch();
        batch.setBatchNumber(numberService.next("FG"));
        batch.setProduct(productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId)));
        batch.setProductionOrderId(productionOrderId);
        batch.setProducedQty(producedQty);
        batch.setRemainingQty(producedQty);
        batch.setProducedOn(producedOn != null ? producedOn : LocalDate.now());
        batch.setQualityStatus("PRODUCTION_COMPLETED");
        batch.setUnitCost(unitCost);
        return fgBatchRepo.save(batch);
    }

    @Transactional(readOnly = true)
    public Page<MaterialBatchDto> searchMaterialBatches(Long materialId, String status, Pageable pageable) {
        return batchRepo.search(materialId, status, pageable).map(MaterialBatchDto::from);
    }

    @Transactional(readOnly = true)
    public MaterialBatchDto findBatchById(Long id) {
        return MaterialBatchDto.from(batchRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Batch not found: " + id)));
    }
}
