package com.mfg.ims.procurement;

import org.springframework.stereotype.Component;

import com.mfg.ims.procurement.porevision.PoRevisionRepository;

/**
 * Guards and inspects PO amendment state.
 * <p>
 * Currently {@code po_revisions} rows are committed immediately on amendment (no DRAFT/PENDING
 * lifecycle), so {@link #amendmentPending(Long)} always returns {@code false}. The field is
 * exposed on {@link com.mfg.ims.procurement.dto.PurchaseOrderDto} for future use.
 */
@Component
public class AmendmentGuard {

    private final PoRevisionRepository revisionRepo;

    public AmendmentGuard(PoRevisionRepository revisionRepo) {
        this.revisionRepo = revisionRepo;
    }

    /**
     * Returns {@code true} if there is a pending (uncommitted) amendment for this PO.
     * Currently po_revisions has no DRAFT/PENDING_APPROVAL status, so this is always false.
     */
    public boolean amendmentPending(Long poId) {
        // po_revisions records are committed immediately; no pending state exists yet
        return false;
    }
}
