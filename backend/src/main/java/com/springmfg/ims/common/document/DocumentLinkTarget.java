package com.springmfg.ims.common.document;

import java.util.Set;

/**
 * A kind of record a document can be attached to. Each module that owns such a record registers one bean, so the
 * document module never imports another module: it only knows this interface.
 */
public interface DocumentLinkTarget {

    /** The value stored in {@code document_links.entity_type} (it must be allowed by that table's check constraint). */
    String entityType();

    boolean exists(Long id);

    /**
     * Authorities of which a caller needs any one to read this kind of record. They should mirror what the record's own
     * read endpoint requires, so a document never opens a door the record itself keeps shut.
     */
    Set<String> readAuthorities();
}
