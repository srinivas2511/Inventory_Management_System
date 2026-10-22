package com.mfg.ims.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.document.domain.DocumentLink;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, Long> {

    List<DocumentLink> findByDocumentId(Long documentId);

    List<DocumentLink> findByDocumentIdIn(Collection<Long> documentIds);

    Optional<DocumentLink> findByDocumentIdAndEntityTypeAndEntityId(Long documentId, String entityType, Long entityId);

    Optional<DocumentLink> findByIdAndDocumentId(Long id, Long documentId);
}
