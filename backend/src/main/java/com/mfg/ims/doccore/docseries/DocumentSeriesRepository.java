package com.mfg.ims.doccore.docseries;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentSeriesRepository extends JpaRepository<DocumentSeries, Long> {

    List<DocumentSeries> findByCompanyIdAndDocumentKindAndActiveTrueOrderBySeriesCode(
            Long companyId, String documentKind);

    List<DocumentSeries> findByCompanyIdAndActiveTrueOrderByDocumentKindAscSeriesCodeAsc(Long companyId);

    boolean existsByCompanyIdAndDocumentKindAndSeriesCode(Long companyId, String documentKind, String seriesCode);

    @Query("SELECT s FROM DocumentSeries s WHERE s.companyId = :companyId AND s.documentKind = :documentKind AND s.isDefault = true AND s.active = true")
    Optional<DocumentSeries> findDefault(Long companyId, String documentKind);
}
