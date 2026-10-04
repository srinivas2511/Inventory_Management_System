package com.springmfg.ims.document.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.springmfg.ims.document.domain.DocumentCategory;
import com.springmfg.ims.document.domain.DocumentLink;

/**
 * Access control for documents beyond the endpoint-level permission. A document linked to records can be read only by
 * someone who may read at least one of those records (each kind of record names its read authorities, see
 * {@link DocumentLinkTargets}); an unlinked document (an SOP, say) is readable by anyone holding DOCUMENT_VIEW;
 * DOCUMENT_MANAGE reads everything. Writing to a link needs read access to the linked record's type, and a category may
 * add its own required authority (drawings need DRAWING_MANAGE).
 */
@Component
public class DocumentAccess {

    private final DocumentLinkTargets targets;

    public DocumentAccess(DocumentLinkTargets targets) {
        this.targets = targets;
    }

    public Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    public boolean seesEverything() {
        return authorities().contains("DOCUMENT_MANAGE");
    }

    public Set<String> readableTypes() {
        Set<String> held = authorities();
        return targets.readAuthorities().entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(held::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean canRead(Collection<DocumentLink> links) {
        if (seesEverything() || links.isEmpty()) {
            return true;
        }
        Set<String> readable = readableTypes();
        return links.stream().anyMatch(l -> readable.contains(l.getEntityType()));
    }

    public void requireRead(Collection<DocumentLink> links) {
        if (!canRead(links)) {
            throw new AccessDeniedException("No access to the records this document is linked to");
        }
    }

    /** Linking a document to a record requires being able to read that kind of record. */
    public void requireCanLink(String entityType) {
        if (!seesEverything() && !readableTypes().contains(entityType)) {
            throw new AccessDeniedException("No access to " + entityType + " records");
        }
    }

    public void requireCategoryAuthority(DocumentCategory category) {
        String required = category.getRequiredAuthority();
        if (required != null && !authorities().contains(required)) {
            throw new AccessDeniedException("The " + category.getCategoryCode() + " category requires " + required);
        }
    }
}
