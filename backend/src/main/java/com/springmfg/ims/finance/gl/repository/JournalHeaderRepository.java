package com.springmfg.ims.finance.gl.repository;

import com.springmfg.ims.finance.gl.domain.JournalHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalHeaderRepository extends JpaRepository<JournalHeader, Long> {

    Optional<JournalHeader> findBySourceModuleAndSourceDocumentTypeAndSourceDocumentId(
            String sourceModule, String sourceDocumentType, Long sourceDocumentId);

    Page<JournalHeader> findByStatus(String status, Pageable pageable);

    Page<JournalHeader> findByPeriodId(Long periodId, Pageable pageable);
}
