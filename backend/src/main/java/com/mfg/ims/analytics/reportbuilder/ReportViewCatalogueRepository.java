package com.mfg.ims.analytics.reportbuilder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportViewCatalogueRepository extends JpaRepository<ReportViewCatalogueEntry, ReportViewCatalogueId> {
    List<ReportViewCatalogueEntry> findByViewName(String viewName);
}
