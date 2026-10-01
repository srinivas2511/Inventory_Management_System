package com.springmfg.ims.masterdata;

import java.util.List;

/**
 * Lets other modules say that a material is still in use, without master data depending on them. A module registers
 * one as a Spring bean: Phase 2 adds "stock on hand", Phase 3 "open purchase orders", Phase 4 "active BOMs".
 * <p>
 * Deactivating a material that is in use needs an explicit {@code force} (DESIGN.md section 5.2: "warn, then
 * require a flag"); changing a material's unit of measure is refused outright while anything still refers to it.
 */
public interface MaterialUsageCheck {

    /** Reasons to hesitate before deactivating, e.g. {@code "3 open purchase orders"}; empty if none. */
    List<String> deactivationWarnings(long materialId);

    /** Reasons the unit of measure may not change, e.g. {@code "stock exists in KG"}; empty if it may. */
    default List<String> unitChangeBlockers(long materialId) {
        return List.of();
    }
}
