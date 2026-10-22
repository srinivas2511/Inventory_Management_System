package com.mfg.ims.wms.domain;

import java.util.EnumSet;
import java.util.Set;

/** Enumerations of the warehouse module, kept together because each is small. */
public final class WmsEnums {

    private WmsEnums() {
    }

    public enum ZoneType { BULK, PICKING, RECEIVING, DISPATCH, QC, STAGING }

    public enum BinType { STANDARD, OVERSIZED, QUARANTINE, QC }

    public enum PutAwayStrategy { FIXED_BIN, NEAREST_EMPTY, BULK_FIRST }

    public enum MovementType { PUTAWAY, PICKING, INTERNAL_TRANSFER, CYCLE_COUNT, CROSS_DOCK }

    public enum TransferOrderStatus {
        CREATED, IN_PROGRESS, COMPLETED, CANCELLED;

        public boolean canMoveTo(TransferOrderStatus next) {
            return switch (this) {
                case CREATED -> next == IN_PROGRESS || next == COMPLETED || next == CANCELLED;
                case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
                case COMPLETED, CANCELLED -> false;
            };
        }

        public boolean isOpen() {
            return this == CREATED || this == IN_PROGRESS;
        }
    }

    public enum TransferLineStatus { OPEN, PARTIAL, CONFIRMED, CANCELLED }

    public enum WaveStatus {
        CREATED, RELEASED, COMPLETED, CANCELLED;

        public boolean canMoveTo(WaveStatus next) {
            return switch (this) {
                case CREATED -> next == RELEASED || next == CANCELLED;
                case RELEASED -> next == COMPLETED || next == CANCELLED;
                case COMPLETED, CANCELLED -> false;
            };
        }
    }

    public enum CycleCountType { ZONE, ABC, RANDOM }

    public enum CycleCountStatus {
        CREATED, IN_PROGRESS, REVIEW, POSTED, CANCELLED;

        private static final Set<CycleCountStatus> COUNTABLE = EnumSet.of(CREATED, IN_PROGRESS);

        public boolean isCountable() {
            return COUNTABLE.contains(this);
        }

        public boolean canMoveTo(CycleCountStatus next) {
            return switch (this) {
                case CREATED -> next == IN_PROGRESS || next == CANCELLED;
                case IN_PROGRESS -> next == REVIEW || next == CANCELLED;
                case REVIEW -> next == IN_PROGRESS || next == POSTED || next == CANCELLED;
                case POSTED, CANCELLED -> false;
            };
        }
    }
}
