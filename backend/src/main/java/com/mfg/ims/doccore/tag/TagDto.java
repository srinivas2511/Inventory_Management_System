package com.mfg.ims.doccore.tag;

public record TagDto(Long id, String name, String color) {
    public static TagDto from(Tag t) { return new TagDto(t.getId(), t.getName(), t.getColor()); }
}
