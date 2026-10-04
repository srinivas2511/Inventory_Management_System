package com.springmfg.ims.document.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.document.domain.Document;
import com.springmfg.ims.document.domain.DocumentStatus;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    /**
     * Every filter takes a sentinel instead of null (PostgreSQL cannot type an untyped null parameter): empty text,
     * category 0, entity id 0 and the full status list mean "no filter". A document is visible when the caller may see
     * everything, when it has no links, or when one of its links is of a type the caller can read.
     */
    @Query("""
            SELECT d FROM Document d
            WHERE (:q = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:categoryId = 0L OR d.categoryId = :categoryId)
              AND d.status IN :statuses
              AND (:entityType = '' OR EXISTS (SELECT 1 FROM DocumentLink l
                    WHERE l.documentId = d.id AND l.entityType = :entityType AND l.entityId = :entityId))
              AND (:seeAll = TRUE
                   OR NOT EXISTS (SELECT 1 FROM DocumentLink l WHERE l.documentId = d.id)
                   OR EXISTS (SELECT 1 FROM DocumentLink l
                              WHERE l.documentId = d.id AND l.entityType IN :readableTypes))
            """)
    Page<Document> searchVisible(@Param("q") String q,
                                 @Param("categoryId") long categoryId,
                                 @Param("statuses") Collection<DocumentStatus> statuses,
                                 @Param("entityType") String entityType,
                                 @Param("entityId") long entityId,
                                 @Param("seeAll") boolean seeAll,
                                 @Param("readableTypes") Collection<String> readableTypes,
                                 Pageable pageable);

    default Page<Document> search(String q, Long categoryId, Collection<DocumentStatus> statuses, String entityType,
                                  Long entityId, boolean seeAll, Collection<String> readableTypes,
                                  Pageable pageable) {
        Collection<DocumentStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? java.util.List.of(DocumentStatus.values()) : statuses;
        Collection<String> types = readableTypes == null || readableTypes.isEmpty()
                ? java.util.List.of("-") : readableTypes;
        return searchVisible(SearchText.normalise(q), categoryId == null ? 0L : categoryId, effectiveStatuses,
                entityType == null ? "" : entityType, entityId == null ? 0L : entityId, seeAll, types, pageable);
    }
}
