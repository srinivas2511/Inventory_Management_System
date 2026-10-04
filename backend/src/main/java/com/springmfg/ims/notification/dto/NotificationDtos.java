package com.springmfg.ims.notification.dto;

import java.time.Instant;
import java.util.List;

import com.springmfg.ims.notification.domain.NotificationChannel;
import com.springmfg.ims.notification.domain.NotificationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationDto(Long id, String templateCode, String subject, String body, String entityType,
                                  Long entityId, Instant createdAt, Instant readAt, boolean read) {
    }

    public record UnreadCountDto(long unread) {
    }

    public record ChannelPreferenceDto(NotificationChannel channel, boolean enabled) {
    }

    public record PreferenceDto(String templateCode, String description, List<ChannelPreferenceDto> channels) {
    }

    public record SetPreferenceRequest(@NotBlank @Size(max = 40) String templateCode,
                                       @NotNull NotificationChannel channel, boolean enabled) {
    }

    public record TemplateDto(Long id, String templateCode, String channel, String description,
                              String subjectTemplate, String bodyTemplate, boolean active) {
    }

    public record SaveTemplateRequest(@NotBlank @Pattern(regexp = "EMAIL|IN_APP|BOTH") String channel,
                                      @Size(max = 200) String description,
                                      @Size(max = 200) String subjectTemplate,
                                      @NotBlank String bodyTemplate, Boolean active) {
    }

    public record DeliveryLogDto(Long id, Long userId, String recipientEmail, String templateCode,
                                 NotificationChannel channel, String subject, NotificationStatus status, int attempts,
                                 Instant nextAttemptAt, String lastError, Instant createdAt, Instant sentAt) {
    }
}
