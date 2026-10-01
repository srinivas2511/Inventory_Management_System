package com.springmfg.ims.masterdata;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity to DTO mapping for master data. Call inside a transaction (the preferred supplier is lazy). */
@Mapper(componentModel = "spring")
interface MasterDataMapper {

    PartnerDtos.PartnerRef toRef(BusinessPartner partner);

    PartnerDtos.PartnerSummary toSummary(BusinessPartner partner);

    PartnerDtos.PartnerResponse toResponse(BusinessPartner partner);

    @Mapping(target = "code", source = "materialCode")
    MaterialDtos.MaterialSummary toSummary(Material material);

    @Mapping(target = "code", source = "materialCode")
    MaterialDtos.MaterialResponse toResponse(Material material);
}
