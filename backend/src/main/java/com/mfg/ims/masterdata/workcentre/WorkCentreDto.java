package com.mfg.ims.masterdata.workcentre;

public record WorkCentreDto(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        Long version) {

    public static WorkCentreDto from(WorkCentre wc) {
        return new WorkCentreDto(wc.getId(), wc.getCode(), wc.getName(),
                wc.getDescription(), wc.isActive(), wc.getVersion());
    }
}
