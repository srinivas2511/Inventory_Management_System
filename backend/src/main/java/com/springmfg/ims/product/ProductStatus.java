package com.springmfg.ims.product;

/**
 * Lifecycle of a product: {@code DRAFT -> ACTIVE <-> OBSOLETE}. Obsolete products stay on record for traceability but
 * cannot be edited or newly used. Status changes only through the activate/obsolete actions, never by editing.
 */
public enum ProductStatus {
    DRAFT, ACTIVE, OBSOLETE
}
