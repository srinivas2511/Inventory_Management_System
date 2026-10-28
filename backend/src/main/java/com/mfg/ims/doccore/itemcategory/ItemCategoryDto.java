package com.mfg.ims.doccore.itemcategory;

public record ItemCategoryDto(
        Long id,
        String categoryCode,
        String name,
        Long parentId,
        String itemType,
        boolean active) {

    public static ItemCategoryDto from(ItemCategory c) {
        return new ItemCategoryDto(c.getId(), c.getCategoryCode(), c.getName(),
                c.getParentId(), c.getItemType(), c.isActive());
    }
}
