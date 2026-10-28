package com.mfg.ims.doccore.search;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSearchService {

    private final SearchIndexRepository repo;

    public GlobalSearchService(SearchIndexRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<SearchResultDto> search(Long companyId, String query, String entityType, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int lim = limit > 0 && limit <= 100 ? limit : 20;
        return repo.searchRaw(companyId, entityType, query, lim)
                .stream()
                .map(row -> new SearchResultDto(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (String) row[2],
                        row[3] != null ? ((Number) row[3]).doubleValue() : 0.0))
                .toList();
    }

    @Async
    @Transactional
    public void indexEntity(Long companyId, String entityType, Long entityId,
                            String textContent, String display) {
        if (textContent == null || textContent.isBlank()) return;
        repo.upsertIndex(companyId, entityType, entityId, textContent, display);
    }
}
