package com.mfg.ims.integration.tally;

import java.util.List;

/** Response DTO for GET /api/integration/tally/readiness. */
public record TallyReadinessDto(
        List<CheckItem> checks,
        boolean ready
) {
    public record CheckItem(String name, boolean ok, String detail) {}
}
