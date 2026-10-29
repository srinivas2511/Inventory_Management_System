package com.mfg.ims.analytics.reportbuilder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
    List<SavedReport> findByCompanyIdOrderByIdDesc(Long companyId);
    Optional<SavedReport> findByIdAndCompanyId(Long id, Long companyId);
}
