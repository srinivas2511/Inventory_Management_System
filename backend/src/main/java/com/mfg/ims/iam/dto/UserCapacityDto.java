package com.mfg.ims.iam.dto;

/** Current user count vs. the configured iam.max_users limit (0 = unlimited). */
public record UserCapacityDto(long total, int limit) {}
