package com.mfg.ims.production.dto;

/** An active user who can be given production work (holds {@code PRODUCTION_EXECUTE}). */
public record AssignableOperatorDto(Long id, String username, String fullName) {
}
