package com.mfg.ims.production.domain;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/** Why a machine stopped. Planned maintenance is the only planned loss; every other category is an unplanned stop. */
public enum DowntimeCategory {
    BREAKDOWN("Breakdown"),
    SETUP_CHANGEOVER("Setup and changeover"),
    MATERIAL_SHORTAGE("Material shortage"),
    NO_OPERATOR("No operator"),
    MINOR_STOP("Minor stop"),
    PLANNED_MAINTENANCE("Planned maintenance"),
    OTHER("Other");

    private final String label;

    DowntimeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isPlanned() {
        return this == PLANNED_MAINTENANCE;
    }

    /** A null or blank category is allowed and counts as OTHER; anything else must be one of the constants. */
    public static DowntimeCategory parseOrOther(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /** For new downtime records: a given category must be a known one. */
    public static String validate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unknown downtime category: " + value + " (expected one of "
                            + java.util.Arrays.toString(values()) + ")");
        }
    }
}
