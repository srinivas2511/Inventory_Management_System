package com.mfg.ims.procurement.porevision;

import java.time.Instant;

public record PoRevisionDto(Long id, Long poId, int revisionNo, String reason, Long createdBy, Instant createdAt) {}
