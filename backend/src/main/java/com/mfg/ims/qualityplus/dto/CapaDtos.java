package com.mfg.ims.qualityplus.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.mfg.ims.qualityplus.domain.CapaSourceType;
import com.mfg.ims.qualityplus.domain.CapaStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CapaDtos {

    private CapaDtos() {
    }

    /** Source type and id are fixed when the CAPA is raised; an update ignores them. */
    public record SaveCapaRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull CapaSourceType sourceType,
            Long sourceId,
            String rootCause,
            String correctiveAction,
            String preventiveAction,
            LocalDate targetCompletionDate,
            Long assignedTo) {
    }

    /** A reason is required for a move back (failed verification). */
    public record AdvanceCapaRequest(@NotNull CapaStatus target, @Size(max = 500) String reason) {
    }

    public record CloseCapaRequest(@NotBlank String verificationNotes) {
    }

    public record CapaSummaryDto(
            Long id, String capaNumber, String title, CapaSourceType sourceType, Long sourceId, CapaStatus status,
            LocalDate targetCompletionDate, Long assignedTo, Instant openedAt, Instant closedAt) {
    }

    public record CapaDto(
            Long id, String capaNumber, String title, String description, CapaSourceType sourceType, Long sourceId,
            String sourceLabel, String rootCause, String correctiveAction, String preventiveAction,
            String verificationNotes, LocalDate targetCompletionDate, CapaStatus status, Set<CapaStatus> nextStatuses,
            Long assignedTo, Long verifiedBy, Instant openedAt, Instant closedAt) {
    }
}
