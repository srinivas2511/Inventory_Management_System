package com.mfg.ims.doccore.transporter;

public record TransporterDto(
        Long id,
        String transporterCode,
        String code,
        String name,
        String gstTransporterId,
        String gstin,
        String contact,
        String phone,
        String email,
        boolean active) {

    public static TransporterDto from(Transporter t) {
        return new TransporterDto(t.getId(), t.getTransporterCode(), t.getTransporterCode(), t.getName(),
                t.getGstTransporterId(), t.getGstTransporterId(), t.getContact(), null, null, t.isActive());
    }
}
