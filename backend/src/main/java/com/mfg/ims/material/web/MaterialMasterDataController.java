package com.mfg.ims.material.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.partner.dto.ApprovedItemDto;
import com.mfg.ims.material.dto.PurchasingInfoDto;
import com.mfg.ims.material.dto.SaveUomConversionRequest;
import com.mfg.ims.material.dto.SavePurchasingInfoRequest;
import com.mfg.ims.partner.dto.SetApprovedItemsRequest;
import com.mfg.ims.material.dto.UomConversionDto;
import com.mfg.ims.material.service.MaterialPurchasingInfoService;
import com.mfg.ims.material.service.SupplierApprovedMaterialService;
import com.mfg.ims.material.service.UomConversionService;

/**
 * Master-data extras of a material: alternative units of measure (MDM-003), purchasing information (MDM-004) and the
 * approved-materials list of a supplier (MDM-020).
 */
@RestController
@RequestMapping("/api")
public class MaterialMasterDataController {

    private final UomConversionService uomConversions;
    private final MaterialPurchasingInfoService purchasingInfo;
    private final SupplierApprovedMaterialService approvedMaterials;

    public MaterialMasterDataController(UomConversionService uomConversions,
                                        MaterialPurchasingInfoService purchasingInfo,
                                        SupplierApprovedMaterialService approvedMaterials) {
        this.uomConversions = uomConversions;
        this.purchasingInfo = purchasingInfo;
        this.approvedMaterials = approvedMaterials;
    }

    @GetMapping("/materials/{id}/uom-conversions")
    @PreAuthorize("hasAnyAuthority('MATERIAL_VIEW','MASTERDATA_MANAGE')")
    public List<UomConversionDto> conversions(@PathVariable Long id) {
        return uomConversions.list(id);
    }

    @PutMapping("/materials/{id}/uom-conversions")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public UomConversionDto saveConversion(@PathVariable Long id, @Valid @RequestBody SaveUomConversionRequest req) {
        return uomConversions.save(id, req);
    }

    @DeleteMapping("/materials/{id}/uom-conversions/{altUom}")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public void deleteConversion(@PathVariable Long id, @PathVariable String altUom) {
        uomConversions.delete(id, altUom);
    }

    @GetMapping("/materials/{id}/purchasing-info")
    @PreAuthorize("hasAnyAuthority('MATERIAL_VIEW','MASTERDATA_MANAGE')")
    public PurchasingInfoDto purchasingInfo(@PathVariable Long id) {
        return purchasingInfo.get(id);
    }

    @PutMapping("/materials/{id}/purchasing-info")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public PurchasingInfoDto savePurchasingInfo(@PathVariable Long id,
                                                @Valid @RequestBody SavePurchasingInfoRequest req) {
        return purchasingInfo.save(id, req);
    }

    @GetMapping("/suppliers/{id}/approved-materials")
    @PreAuthorize("hasAnyAuthority('SUPPLIER_MANAGE','MASTERDATA_MANAGE')")
    public List<ApprovedItemDto> approvedMaterials(@PathVariable Long id) {
        return approvedMaterials.list(id);
    }

    @PutMapping("/suppliers/{id}/approved-materials")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public List<ApprovedItemDto> setApprovedMaterials(@PathVariable Long id,
                                                      @Valid @RequestBody SetApprovedItemsRequest req) {
        return approvedMaterials.replace(id, req.ids());
    }
}
