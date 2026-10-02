package com.springmfg.ims.quality.dto;

public record RejectionReasonDto(
        Long id,
        String reasonCode,
        String name,
        String appliesTo,
        boolean active
) {}
