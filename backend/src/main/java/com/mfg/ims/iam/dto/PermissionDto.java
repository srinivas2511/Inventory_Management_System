package com.mfg.ims.iam.dto;

import com.mfg.ims.iam.domain.Permission;

public record PermissionDto(Long id, String code, String module, String description) {

    public static PermissionDto from(Permission p) {
        return new PermissionDto(p.getId(), p.getCode(), p.getModule(), p.getDescription());
    }
}
