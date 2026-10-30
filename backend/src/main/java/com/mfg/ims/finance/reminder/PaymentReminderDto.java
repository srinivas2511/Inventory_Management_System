package com.mfg.ims.finance.reminder;

import java.time.Instant;

/** Read/write DTO for a payment reminder. */
public record PaymentReminderDto(
        Long id,
        Long openItemId,
        Long partyId,
        Integer level,
        String mode,
        Instant sentAt,
        String channel,
        Long sentBy,
        Instant createdAt
) {}
