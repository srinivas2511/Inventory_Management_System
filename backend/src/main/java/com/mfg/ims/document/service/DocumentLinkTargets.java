package com.mfg.ims.document.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.document.DocumentLinkTarget;

/**
 * The kinds of record documents can be linked to, collected from the modules that own them (see
 * {@link DocumentLinkTarget}). This class knows no module, so adding a linkable record is one new bean elsewhere.
 */
@Component
public class DocumentLinkTargets {

    private final Map<String, DocumentLinkTarget> byType = new LinkedHashMap<>();

    public DocumentLinkTargets(List<DocumentLinkTarget> targets) {
        for (DocumentLinkTarget t : targets) {
            if (byType.put(t.entityType(), t) != null) {
                throw new IllegalStateException("Two document link targets claim " + t.entityType());
            }
        }
    }

    public Set<String> types() {
        return byType.keySet();
    }

    public boolean exists(String entityType, Long entityId) {
        DocumentLinkTarget target = entityType == null ? null : byType.get(entityType);
        return target != null && entityId != null && target.exists(entityId);
    }

    /** Entity type -> authorities of which the caller needs any one to read that kind of record. */
    public Map<String, Set<String>> readAuthorities() {
        return byType.values().stream()
                .collect(Collectors.toMap(DocumentLinkTarget::entityType, DocumentLinkTarget::readAuthorities));
    }
}
