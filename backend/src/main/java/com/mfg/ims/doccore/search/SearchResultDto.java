package com.mfg.ims.doccore.search;

public record SearchResultDto(String entityType, Long entityId, String display, double rank) {}
