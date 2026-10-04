package com.springmfg.ims.document.domain;

/** Document lifecycle: DRAFT -> REVIEW -> APPROVED -> OBSOLETE, with REVIEW -> DRAFT as the return path. */
public enum DocumentStatus {
    DRAFT, REVIEW, APPROVED, OBSOLETE;

    public boolean canMoveTo(DocumentStatus target) {
        return switch (this) {
            case DRAFT -> target == REVIEW || target == OBSOLETE;
            case REVIEW -> target == APPROVED || target == DRAFT;
            case APPROVED -> target == OBSOLETE;
            case OBSOLETE -> false;
        };
    }
}
