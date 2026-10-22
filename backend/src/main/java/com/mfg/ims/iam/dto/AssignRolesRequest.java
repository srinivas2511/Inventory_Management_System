package com.mfg.ims.iam.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

public record AssignRolesRequest(@NotNull Set<String> roleCodes) {
}
