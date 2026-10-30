package com.mfg.ims.production.serviceorder;

import java.time.Instant;

public record ServiceConfirmationDto(Long id, Long soId, Instant confirmedAt, Long confirmedBy, String remarks) {}
