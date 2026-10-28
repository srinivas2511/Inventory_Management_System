package com.mfg.ims.doccore.companyprofile;

public record CompanyProfileDto(
        Long id,
        Long companyId,
        String legalName,
        String tradeName,
        String pan,
        String cin,
        String gstin,
        String registeredAddress,
        String phone,
        String email,
        String logoStorageKey,
        Long defaultPaymentTermsId,
        int fiscalYearStartMonth,
        String website) {

    public static CompanyProfileDto from(CompanyProfile p) {
        return new CompanyProfileDto(p.getId(), p.getCompanyId(), p.getLegalName(), p.getTradeName(),
                p.getPan(), p.getCin(), null, null, null, null,
                p.getLogoStorageKey(), p.getDefaultPaymentTermsId(),
                p.getFiscalYearStartMonth(), p.getWebsite());
    }
}
