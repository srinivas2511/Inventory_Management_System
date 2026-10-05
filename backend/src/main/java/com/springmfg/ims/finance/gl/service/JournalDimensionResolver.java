package com.springmfg.ims.finance.gl.service;

import java.util.Optional;

/**
 * Lets the module that raised a stock movement choose the cost centre its auto-journal is charged to, instead of the
 * default on the account assignment. A module registers one by publishing a bean; the bridge asks each in turn.
 */
public interface JournalDimensionResolver {

    /** The cost centre for a movement caused by the given document, or empty to keep the assignment's default. */
    Optional<Long> costCentreFor(String referenceType, Long referenceId);
}
