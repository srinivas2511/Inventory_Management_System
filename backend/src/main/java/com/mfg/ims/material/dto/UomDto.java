package com.mfg.ims.material.dto;

import com.mfg.ims.material.domain.Uom;

public record UomDto(String code, String name, String kind) {

    public static UomDto from(Uom u) {
        return new UomDto(u.getCode(), u.getName(), u.getKind());
    }
}
