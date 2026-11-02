package com.mfg.ims.procurement.vendortype;

public record VendorTypeDto(
        Long id,
        String code,
        String description,
        boolean active,
        Long version) {

    public static VendorTypeDto from(VendorType vt) {
        return new VendorTypeDto(vt.getId(), vt.getCode(), vt.getDescription(),
                vt.isActive(), vt.getVersion());
    }
}
