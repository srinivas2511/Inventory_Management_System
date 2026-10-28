package com.mfg.ims.doccore.search;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SearchIndexRepository extends JpaRepository<SearchIndex, Long> {

    @Query(value = """
            SELECT entity_type, entity_id, display,
                   ts_rank(text_vector, websearch_to_tsquery('english', :query)) AS rank
            FROM ims.search_index
            WHERE company_id = :companyId
              AND (:entityType IS NULL OR entity_type = :entityType)
              AND text_vector @@ websearch_to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> searchRaw(Long companyId, String entityType, String query, int lim);

    @Modifying
    @Query(value = """
            INSERT INTO ims.search_index (entity_type, entity_id, text_vector, display, updated_at, company_id)
            VALUES (:entityType, :entityId, to_tsvector('english', :text), :display, now(), :companyId)
            ON CONFLICT (company_id, entity_type, entity_id) DO UPDATE
              SET text_vector = to_tsvector('english', :text),
                  display = :display,
                  updated_at = now()
            """, nativeQuery = true)
    void upsertIndex(Long companyId, String entityType, Long entityId, String text, String display);
}
