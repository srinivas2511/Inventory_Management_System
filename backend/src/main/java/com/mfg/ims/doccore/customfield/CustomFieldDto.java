package com.mfg.ims.doccore.customfield;

public record CustomFieldDto(
        Long id,
        String entityType,
        String fieldKey,
        String label,
        String fieldType,
        String options,
        boolean mandatory,
        boolean showInList,
        int sortOrder,
        boolean active) {

    public static CustomFieldDto from(CustomFieldDefinition f) {
        return new CustomFieldDto(f.getId(), f.getEntityType(), f.getFieldKey(), f.getLabel(),
                f.getFieldType(), f.getOptions(), f.isMandatory(), f.isShowInList(),
                f.getSortOrder(), f.isActive());
    }
}
